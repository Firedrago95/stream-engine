package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
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
            )
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

        List<Long> deltas = new ArrayList<>(Collections.nCopies(110, 4L));
        deltas.addAll(Collections.nCopies(10, 35L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 5000);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.MEGA);
        assertThat(tierInfo.isMega()).isTrue();
        assertThat(tierInfo.noiseFloor()).isEqualTo(12L);
        assertThat(tierInfo.zScoreThreshold()).isEqualTo(3.0);
    }

    @Test
    void 평균과_피크_조건을_만족하면_REGULAR_체급으로_정상_승급한다() {
        // given
        String streamId = "stream-regular";
        streamProvider.setActiveStreamIds(List.of(streamId));

        List<Long> deltas = new ArrayList<>(Collections.nCopies(115, 1L));
        deltas.addAll(Collections.nCopies(5, 12L));
        repository.setFirepowerDeltas(streamId, deltas);

        // when
        streamTierManager.refreshAllTiers();
        StreamTierInfo tierInfo = streamTierManager.getTierInfo(streamId, 300);

        // then
        assertThat(tierInfo.tier()).isEqualTo(StreamTier.REGULAR);
        assertThat(tierInfo.noiseFloor()).isEqualTo(6L);
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
}
