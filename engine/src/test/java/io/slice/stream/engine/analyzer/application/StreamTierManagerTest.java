package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.GroupProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.TierProperties;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.analyzer.fake.FakeActiveStreamProvider;
import io.slice.stream.engine.analyzer.fake.FakeChatRoomAggregationRepository;
import java.time.Instant;
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
            12000L,
            15,
            0.99,
            0.005,
            new TierProperties(
                new GroupProperties(180, 3.0, 3.0, 30, 12L),
                new GroupProperties(180, 3.5, 0.8, 10, 6L),
                new GroupProperties(180, 4.0, 0.0, 0, 5L)
            ),
            new DynamicFloorProperties(5L, 4.0, 4.0)
        );

        streamTierManager = new StreamTierManager(streamProvider, repository, props);
    }

    @Test
    void 방송_틱이_100개_미만이면_승급_검사를_유보하고_MICRO_체급을_유지한다() {
        // given
        String streamId = "stream-short";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> shortDeltas = new ArrayList<>(Collections.nCopies(50, 50L));
        repository.setFirepowerDeltas(streamId, shortDeltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 1000);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.MICRO);
    }

    @Test
    void 도네이션으로_피크만_튀고_평균이_낮으면_MEGA로_오승급되지_않고_MICRO나_REGULAR를_유지한다() {
        // given
        String streamId = "stream-donation-burst";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(119, 0L));
        deltas.add(35L);
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 50);

        // then
        assertThat(tierInfo.tier()).isNotEqualTo(StreamTier.MEGA);
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.MICRO);
    }

    @Test
    void 평균과_피크_조건을_모두_만족하면_MEGA_체급으로_정상_승급한다() {
        // given
        String streamId = "stream-mega";
        streamProvider.setActiveStreamIds(List.of(streamId));

        // 1시간(1200틱) 중 앞쪽 600틱은 1L, 최근 30분(뒤쪽 600틱)은 500틱 4L + 100틱 35L
        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 1L));
        deltas.addAll(Collections.nCopies(500, 4L));
        deltas.addAll(Collections.nCopies(100, 35L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 5000);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.MEGA);
        assertThat(tierInfo.isMega()).isTrue();
        assertThat(tierInfo.noiseFloor()).isEqualTo(41L);
        assertThat(tierInfo.zScoreThreshold()).isEqualTo(3.0);
    }

    @Test
    void 평균과_피크_조건을_만족하면_REGULAR_체급으로_정상_승급한다() {
        // given
        String streamId = "stream-regular";
        streamProvider.setActiveStreamIds(List.of(streamId));

        // 1시간(1200틱) 중 앞쪽 600틱은 0L, 최근 30분(뒤쪽 600틱)은 550틱 1L + 50틱 12L
        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 0L));
        deltas.addAll(Collections.nCopies(550, 1L));
        deltas.addAll(Collections.nCopies(50, 12L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 300);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.REGULAR);
        assertThat(tierInfo.noiseFloor()).isEqualTo(12L);
        assertThat(tierInfo.zScoreThreshold()).isEqualTo(3.5);
    }

    @Test
    void 콜드스타트_시_안전하게_MICRO_체급과_기본_바닥값을_부여한다() {
        // given & when
        StreamTierInfo tierInfo = streamTierManager.getTierInfo("new-streamer", 100);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.MICRO);
        assertThat(tierInfo.noiseFloor()).isEqualTo(5L);
        assertThat(tierInfo.minFirepowerCutoff()).isGreaterThanOrEqualTo(5L);
    }

    @Test
    void 방송_초반_30분_미만의_데이터는_절반으로_자르지_않고_가용_전체_데이터를_반영한다() {
        // given: 방송 20분 경과(400틱), 앞 200틱 화력 1L, 뒤 200틱 화력 10L (전체 평균 5.5)
        String streamId = "stream-early";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(200, 1L));
        deltas.addAll(Collections.nCopies(200, 10L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 500);

        // then: round(4.0 * 5.5 + 4.0) = 26L (뒤쪽 절반만 자르면 44L가 되지만, 가용 400틱 전체 반영으로 26L)
        assertThat(tierInfo.noiseFloor()).isEqualTo(26L);
    }

    @Test
    void 화력_비례_동적_바닥값이_평균_화력에_따라_연속적으로_산출된다() {
        // given
        String streamId = "stream-dynamic-floor";
        streamProvider.setActiveStreamIds(List.of(streamId));

        // 평균 화력 10.0 건/3초 세팅 (600틱 모두 10L)
        List<Long> deltas = new ArrayList<>(Collections.nCopies(600, 10L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 3000);

        // then: round(4.0 * 10.0 + 4.0) = 44L
        assertThat(tierInfo.noiseFloor()).isEqualTo(44L);
    }
}
