package io.slice.stream.apiserver.stream.infrastructure.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionEntityTest {

    @Test
    void 세션의_방제와_카테고리_메타데이터를_갱신한다() {
        // given
        Instant now = Instant.now();
        StreamSessionEntity session = new StreamSessionEntity("streamId", "sessionId", "이전방제", "이전카테고리", now);

        // when
        session.updateMetadata("새로운방제", "새로운카테고리");

        // then
        assertThat(session.getTitle()).isEqualTo("새로운방제");
        assertThat(session.getCategoryName()).isEqualTo("새로운카테고리");
    }
    @Test
    void 세션_종료_시_종료_시각과_시청자_수_피크치를_기록한다() {
        // given
        Instant now = Instant.now();
        StreamSessionEntity session = new StreamSessionEntity("streamId", "sessionId", "방제", "카테고리", now.minusSeconds(3600));
        Instant endedAt = Instant.now();

        // when
        session.finishSession(endedAt, 1500);

        // then
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(session.getPeakViewers()).isEqualTo(1500);
    }
    @Test
    void 세션_종료_시_전달된_시청자_수가_기존_피크치보다_적으면_피크치를_갱신하지_않는다() {
        // given
        Instant now = Instant.now();
        StreamSessionEntity session = new StreamSessionEntity("streamId", "sessionId", "방제", "카테고리", now.minusSeconds(3600));

        // when
        session.finishSession(now.minusSeconds(1800), 2000);

        Instant endedAt = Instant.now();
        session.finishSession(endedAt, 1500);

        // then
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(session.getPeakViewers()).isEqualTo(2000);
    }
}
