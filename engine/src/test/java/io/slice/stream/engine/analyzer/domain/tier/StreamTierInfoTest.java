package io.slice.stream.engine.analyzer.domain.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamTierInfoTest {

    @Test
    void 마스킹_제외_틱이_0이면_마스킹을_끈_상태로_정상_생성된다() {
        StreamTierInfo info = StreamTierInfo.builder()
            .streamId("stream-1")
            .tier(StreamTier.REGULAR)
            .minFirepowerCutoff(10L)
            .noiseFloor(6L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(3.5)
            .maskingExclusionTicks(0)
            .build();

        assertThat(info.maskingExclusionTicks()).isZero();
    }

    @Test
    void 윈도우_틱수가_0_이하이면_예외가_발생한다() {
        assertThatThrownBy(() -> StreamTierInfo.builder()
            .streamId("stream-1")
            .tier(StreamTier.REGULAR)
            .minFirepowerCutoff(10L)
            .noiseFloor(6L)
            .windowSeconds(180)
            .windowTicks(0)
            .zScoreThreshold(3.5)
            .maskingExclusionTicks(4)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 마스킹_제외_틱수가_음수이면_예외가_발생한다() {
        assertThatThrownBy(() -> StreamTierInfo.builder()
            .streamId("stream-1")
            .tier(StreamTier.REGULAR)
            .minFirepowerCutoff(10L)
            .noiseFloor(6L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(3.5)
            .maskingExclusionTicks(-1)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
