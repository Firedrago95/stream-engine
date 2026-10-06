package io.slice.stream.engine.analyzer.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightEnginePropertiesTest {

    private HighlightEngineProperties createProperties(long schedulerIntervalMs, long aggregationIntervalMs) {
        return new HighlightEngineProperties(
            schedulerIntervalMs,
            180_000L,
            aggregationIntervalMs,
            900_000L,
            300_000L,
            420_000L,
            15,
            0.005,
            new DynamicFloorProperties(5L, 4.0, 4.0)
        );
    }

    @Test
    void 틱수_계산은_분석_실행_주기가_아닌_데이터_집계_간격을_기준으로_수행된다() {
        HighlightEngineProperties props = createProperties(6000L, 3000L);

        assertThat(props.getRecentWindowTickCount()).isEqualTo(300);
        assertThat(props.getMinDataPointCount()).isEqualTo(100);
    }

    @Test
    void 데이터_집계_간격이_변경되면_변경된_간격에_맞춰_틱수가_계산된다() {
        HighlightEngineProperties props = createProperties(3000L, 6000L);

        assertThat(props.getRecentWindowTickCount()).isEqualTo(150);
        assertThat(props.getMinDataPointCount()).isEqualTo(50);
    }

    @Test
    void 집계_간격이_0_이하이면_예외가_발생한다() {
        assertThatThrownBy(() -> createProperties(3000L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 필수_설정값이_0_이하이거나_누락되면_예외가_발생한다() {
        assertThatThrownBy(() -> new HighlightEngineProperties(
            3000L, 180_000L, 3000L, 0L, 300_000L, 420_000L, 15, 0.005, new DynamicFloorProperties(5L, 4.0, 4.0)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new HighlightEngineProperties(
            3000L, 180_000L, 3000L, 900_000L, 300_000L, 0L, 15, 0.005, new DynamicFloorProperties(5L, 4.0, 4.0)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new HighlightEngineProperties(
            3000L, 180_000L, 3000L, 900_000L, 300_000L, 420_000L, 15, 0.005, null
        )).isInstanceOf(NullPointerException.class);
    }
}
