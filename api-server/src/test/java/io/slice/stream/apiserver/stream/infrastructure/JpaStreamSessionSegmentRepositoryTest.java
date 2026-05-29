package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JpaStreamSessionSegmentRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaStreamSessionSegmentRepository segmentRepository;

    @Test
    void 세션_ID로_열려있는_활성_세그먼트를_조회한다() {
        // given
        String sessionId = "session-1";
        Instant now = Instant.now();

        StreamSessionSegmentEntity closedSegment = new StreamSessionSegmentEntity("stream-1", sessionId, "방제1", "카테고리1", now.minus(2, ChronoUnit.HOURS), 0L);
        closedSegment.endSegment(now.minus(1, ChronoUnit.HOURS), 3600000L);
        segmentRepository.save(closedSegment);

        StreamSessionSegmentEntity openSegment = new StreamSessionSegmentEntity("stream-1", sessionId, "방제2", "카테고리2", now.minus(1, ChronoUnit.HOURS), 3600000L);
        segmentRepository.save(openSegment);

        // when
        Optional<StreamSessionSegmentEntity> activeSegment = segmentRepository.findActiveSegment(sessionId);

        // then
        assertThat(activeSegment).isPresent();
        assertThat(activeSegment.get().getTitle()).isEqualTo("방제2");
        assertThat(activeSegment.get().getEndedAt()).isNull();
    }

    @Test
    void 여러_세션_ID에_대해_현재_열려있는_세그먼트들만_일괄_조회한다() {
        // given
        String session1 = "session-a";
        String session2 = "session-b";
        String session3 = "session-c";
        Instant now = Instant.now();

        StreamSessionSegmentEntity segment1 = new StreamSessionSegmentEntity("stream-1", session1, "방제A", "카테고리A", now, 0L);
        segmentRepository.save(segment1);

        StreamSessionSegmentEntity segment2 = new StreamSessionSegmentEntity("stream-1", session2, "방제B", "카테고리B", now.minus(2, ChronoUnit.HOURS), 0L);
        segment2.endSegment(now.minus(1, ChronoUnit.HOURS), 3600000L);
        segmentRepository.save(segment2);

        StreamSessionSegmentEntity segment3 = new StreamSessionSegmentEntity("stream-1", session3, "방제C", "카테고리C", now, 0L);
        segmentRepository.save(segment3);

        // when
        List<StreamSessionSegmentEntity> activeSegments = segmentRepository.findAllActiveSegments(List.of(session1, session2, session3));

        // then
        assertThat(activeSegments).hasSize(2);
        List<String> activeSessionIds = activeSegments.stream().map(StreamSessionSegmentEntity::getSessionId).toList();
        assertThat(activeSessionIds).containsExactlyInAnyOrder("session-a", "session-c");
    }
}
