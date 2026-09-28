package io.slice.stream.apiserver.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSimilarityPropertiesTest {

    @Test
    void 기본_팩토리_메서드로_생성시_기본_임계값들이_설정된다() {
        StreamerSimilarityProperties props = StreamerSimilarityProperties.defaultProperties();

        assertAll(
            () -> assertThat(props.minSampleSize()).isEqualTo(100),
            () -> assertThat(props.minSimilarityThreshold()).isEqualTo(3.0),
            () -> assertThat(props.topRankLimit()).isEqualTo(3)
        );
    }

    @Test
    void 음수_또는_0_이하의_값이_전달되면_기본값으로_보정된다() {
        StreamerSimilarityProperties props = new StreamerSimilarityProperties(0, -1.0, 0);

        assertAll(
            () -> assertThat(props.minSampleSize()).isEqualTo(100),
            () -> assertThat(props.minSimilarityThreshold()).isEqualTo(3.0),
            () -> assertThat(props.topRankLimit()).isEqualTo(3)
        );
    }

    @Test
    void 정상_설정값이_전달되면_해당_값으로_생성된다() {
        StreamerSimilarityProperties props = new StreamerSimilarityProperties(50, 5.5, 5);

        assertAll(
            () -> assertThat(props.minSampleSize()).isEqualTo(50),
            () -> assertThat(props.minSimilarityThreshold()).isEqualTo(5.5),
            () -> assertThat(props.topRankLimit()).isEqualTo(5)
        );
    }
}
