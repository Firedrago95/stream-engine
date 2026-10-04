package io.slice.stream.engine.analyzer.domain.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamTierInfoTest {

    @Test
    void 정상적인_바닥값으로_StreamTierInfo를_생성한다() {
        StreamTierInfo info = StreamTierInfo.builder()
            .streamId("stream-1")
            .noiseFloor(6L)
            .build();

        assertThat(info.streamId()).isEqualTo("stream-1");
        assertThat(info.noiseFloor()).isEqualTo(6L);
    }

    @Test
    void 바닥값이_음수이면_예외가_발생한다() {
        assertThatThrownBy(() -> StreamTierInfo.builder()
            .streamId("stream-1")
            .noiseFloor(-1L)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
