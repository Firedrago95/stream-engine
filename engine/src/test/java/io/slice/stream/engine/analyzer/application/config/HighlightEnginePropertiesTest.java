package io.slice.stream.engine.analyzer.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.GroupProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.TierProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightEnginePropertiesTest {

    private HighlightEngineProperties createProperties(long schedulerIntervalMs, long aggregationIntervalMs, long maskingTimeMs) {
        return new HighlightEngineProperties(
            schedulerIntervalMs,
            180_000L,
            aggregationIntervalMs,
            maskingTimeMs,
            15,
            0.99,
            0.005,
            new TierProperties(
                new GroupProperties(180, 4.0, 3.0, 30, 16L),
                new GroupProperties(180, 4.0, 0.8, 10, 9L),
                new GroupProperties(180, 4.0, 0.0, 0, 5L)
            ),
            new DynamicFloorProperties(5L, 4.0, 4.0)
        );
    }

    @Test
    void 틱수_계산은_분석_실행_주기가_아닌_데이터_집계_간격을_기준으로_수행된다() {
        HighlightEngineProperties props = createProperties(6000L, 3000L, 12000L);

        assertThat(props.getWindowTickCount(180)).isEqualTo(60);
        assertThat(props.getRecentWindowTickCount()).isEqualTo(600);
        assertThat(props.getMinTierDataPointCount()).isEqualTo(100);
        assertThat(props.getMaskingTickCount()).isEqualTo(4);
    }

    @Test
    void 데이터_집계_간격이_변경되면_변경된_간격에_맞춰_틱수가_계산된다() {
        HighlightEngineProperties props = createProperties(3000L, 6000L, 12000L);

        assertThat(props.getWindowTickCount(180)).isEqualTo(30);
        assertThat(props.getRecentWindowTickCount()).isEqualTo(300);
        assertThat(props.getMinTierDataPointCount()).isEqualTo(50);
        assertThat(props.getMaskingTickCount()).isEqualTo(2);
    }

    @Test
    void 마스킹_시간이_0이면_마스킹_틱수는_0이다() {
        HighlightEngineProperties props = createProperties(3000L, 3000L, 0L);

        assertThat(props.getMaskingTickCount()).isZero();
    }

    @Test
    void 집계_간격이_0_이하이면_기본값인_3000ms로_보정된다() {
        HighlightEngineProperties props = createProperties(3000L, 0L, 12000L);

        assertThat(props.aggregationIntervalMs()).isEqualTo(3000L);
        assertThat(props.getWindowTickCount(180)).isEqualTo(60);
    }
}
