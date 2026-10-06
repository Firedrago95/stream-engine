package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.analyzer.fake.FakeActiveStreamProvider;
import io.slice.stream.engine.analyzer.fake.FakeChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.fake.FakeSessionTierRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamTierManagerTest {

    private StreamTierManager streamTierManager;
    private FakeActiveStreamProvider streamProvider;
    private FakeChatRoomAggregationRepository repository;
    private FakeSessionTierRepository sessionTierRepository;
    private HighlightEngineProperties props;

    @BeforeEach
    void setUp() {
        streamProvider = new FakeActiveStreamProvider();
        repository = new FakeChatRoomAggregationRepository();
        sessionTierRepository = new FakeSessionTierRepository();

        props = new HighlightEngineProperties(
            3000L,
            180000L,
            3000L,
            900000L,
            600000L,
            420000L,
            15,
            0.005,
            new DynamicFloorProperties(5L, 4.0, 4.0)
        );

        streamTierManager = new StreamTierManager(streamProvider, repository, sessionTierRepository, props);
    }

    @Test
    void 방송_데이터가_200틱_미만이면_바닥값_갱신을_유보하고_기본_콜드스타트_값을_유지한다() {
        String streamId = "stream-short";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> shortDeltas = new ArrayList<>(Collections.nCopies(150, 50L));
        repository.setFirepowerDeltas(streamId, shortDeltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 100);

        assertThat(tierInfo.noiseFloor()).isEqualTo(8L);
    }

    @Test
    void 콜드스타트_시_시청자수_체급별_고정_바닥값_F0을_부여한다() {
        StreamTierInfo smallRoom = streamTierManager.getTierInfo("small", 100);
        StreamTierInfo mediumRoom = streamTierManager.getTierInfo("medium", 2000);
        StreamTierInfo largeRoom = streamTierManager.getTierInfo("large", 6000);

        assertThat(smallRoom.noiseFloor()).isEqualTo(8L);
        assertThat(mediumRoom.noiseFloor()).isEqualTo(20L);
        assertThat(largeRoom.noiseFloor()).isEqualTo(40L);
    }

    @Test
    void 화력_비례_동적_바닥값이_15분_평균_화력에_따라_산출된다() {
        String streamId = "stream-dynamic";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(300, 10L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 1000);

        assertThat(tierInfo.noiseFloor()).isEqualTo(44L);
    }

    @Test
    void 평균_화력이_0인_초저화력방도_최소_바닥값_5를_보장한다() {
        String streamId = "stream-quiet";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(300, 0L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 50);

        assertThat(tierInfo.noiseFloor()).isEqualTo(5L);
    }

    @Test
    void 중형방_평균_화력_2점0일_때_바닥값_12가_산출된다() {
        String streamId = "stream-medium";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(300, 2L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 300);

        assertThat(tierInfo.noiseFloor()).isEqualTo(12L);
    }

    @Test
    void L1_캐시_미스_시_Redis_L2에_이전_세션_바닥값이_존재하면_이를_복원하여_적용한다() {
        String streamId = "stream-reconnected";
        long liveId = 12345L;
        sessionTierRepository.save(liveId, 55L);

        // 시청자가 6000명이므로 신규 방송이면 F0=40이어야 하지만, L2에 이전 바닥값(55)이 있으므로 복원되어야 함
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, liveId, 6000);

        assertThat(tierInfo.noiseFloor()).isEqualTo(55L);
    }

    @Test
    void L1과_L2_모두_데이터가_없으면_신규_방송으로_판단하여_콜드스타트_F0을_적용한다() {
        String streamId = "stream-new";
        long liveId = 99999L;

        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, liveId, 6000);

        assertThat(tierInfo.noiseFloor()).isEqualTo(40L);
    }

    @Test
    void 동적_바닥값_갱신_시_L1_캐시와_함께_L2_Redis_저장소에도_세션별로_저장된다() {
        String streamId = "stream-persist";
        long liveId = 77777L;

        streamProvider.setTargets(List.of(
            new StreamTarget(streamId, "streamer", "chat", liveId, "title", 1000, "", "", null)
        ));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(300, 10L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();

        assertThat(sessionTierRepository.findByLiveId(liveId)).contains(44L);
    }

    @Test
    void 동일_채널이_리방_시_새로운_liveId가_부여되어도_보존_시간_내에는_이전_세션의_동적_바닥값을_계승한다() {
        String streamId = "stream-relive";
        long previousLiveId = 11111L;
        long newLiveId = 22222L;

        streamProvider.setTargets(List.of(
            new StreamTarget(streamId, "streamer", "chat", previousLiveId, "title", 6000, "", "", null)
        ));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(300, 10L));
        repository.setFirepowerDeltas(streamId, deltas);
        streamTierManager.refreshAllTiers();

        // 1. 이전 세션에서 동적 바닥값(44) 산출 확인
        StreamTierInfo oldTier = streamTierManager.getTierInfo(streamId, previousLiveId, 6000);
        assertThat(oldTier.noiseFloor()).isEqualTo(44L);

        // 2. 7분 이내에 리방되어 새로운 liveId로 조회 시 콜드스타트(40)가 아닌 이전 바닥값(44)을 계승해야 함
        StreamTierInfo reLiveTier = streamTierManager.getTierInfo(streamId, newLiveId, 6000);
        assertThat(reLiveTier.noiseFloor()).isEqualTo(44L);

        // 3. 신규 liveId에도 바닥값이 세션 저장소에 백업 저장되어야 함
        assertThat(sessionTierRepository.findByLiveId(newLiveId)).contains(44L);
    }
}
