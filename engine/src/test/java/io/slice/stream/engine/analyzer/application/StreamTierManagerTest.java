package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.analyzer.fake.FakeActiveStreamProvider;
import io.slice.stream.engine.analyzer.fake.FakeChatRoomAggregationRepository;
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
    private HighlightEngineProperties props;

    @BeforeEach
    void setUp() {
        streamProvider = new FakeActiveStreamProvider();
        repository = new FakeChatRoomAggregationRepository();

        props = new HighlightEngineProperties(
            3000L,
            180000L,
            3000L,
            1800000L,
            300000L,
            15,
            0.005,
            new DynamicFloorProperties(5L, 5.0, 4.0)
        );

        streamTierManager = new StreamTierManager(streamProvider, repository, props);
    }

    @Test
    void 방송_데이터가_100틱_미만이면_바닥값_갱신을_유보하고_기본_콜드스타트_값을_유지한다() {
        String streamId = "stream-short";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> shortDeltas = new ArrayList<>(Collections.nCopies(50, 50L));
        repository.setFirepowerDeltas(streamId, shortDeltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 100);

        assertThat(tierInfo.noiseFloor()).isEqualTo(5L);
    }

    @Test
    void 콜드스타트_시_시청자수_가중치와_최소_바닥값_중_큰_값을_부여한다() {
        StreamTierInfo smallRoom = streamTierManager.getTierInfo("small", 100);
        StreamTierInfo largeRoom = streamTierManager.getTierInfo("large", 2000);

        assertThat(smallRoom.noiseFloor()).isEqualTo(5L);
        assertThat(largeRoom.noiseFloor()).isEqualTo(10L);
    }

    @Test
    void 화력_비례_동적_바닥값이_30분_평균_화력에_따라_산출된다() {
        String streamId = "stream-dynamic";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 10L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 1000);

        assertThat(tierInfo.noiseFloor()).isEqualTo(54L);
    }

    @Test
    void 평균_화력이_0인_초저화력방도_최소_바닥값_5를_보장한다() {
        String streamId = "stream-quiet";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 0L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 50);

        assertThat(tierInfo.noiseFloor()).isEqualTo(5L);
    }

    @Test
    void 중형방_평균_화력_2점0일_때_바닥값_14가_산출된다() {
        String streamId = "stream-medium";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 2L));
        repository.setFirepowerDeltas(streamId, deltas);

        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 300);

        assertThat(tierInfo.noiseFloor()).isEqualTo(14L);
    }
}
