package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightCleanupSchedulerTest {

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaStreamSessionSegmentRepository segmentRepository;

    @Mock
    private JpaHighlightEventRepository highlightRepository;

    @InjectMocks
    private HighlightCleanupScheduler scheduler;

    @Spy
    private HighlightProperties properties = new HighlightProperties(
        Duration.ofSeconds(20),
        Duration.ofSeconds(5),
        Duration.ofSeconds(90),
        0.7,
        5,
        6,
        20,
        10,
        24,
        30
    );

    @Test
    void 하루가_지난_종료된_세션들에_대해_청소_로직을_실행한다() {
        String sessionId = "old-session-id";
        StreamSessionEntity oldSession = mock(StreamSessionEntity.class);
        given(oldSession.getSessionId()).willReturn(sessionId);

        given(sessionRepository.findFinishedSessionsOlderThan(any(Instant.class)))
            .willReturn(List.of(oldSession))
            .willReturn(List.of());

        scheduler.cleanupOldHighlights();

        verify(sessionRepository, org.mockito.Mockito.atLeastOnce()).findFinishedSessionsOlderThan(any(Instant.class));
        verify(highlightRepository).deleteExceptTop(sessionId, 10);
    }

    @Test
    void 삼십일이_지난_만료된_세션과_연관_데이터를_완전히_삭제한다() {
        String expiredSessionId = "expired-session-id";
        StreamSessionEntity expiredSession = mock(StreamSessionEntity.class);
        given(expiredSession.getSessionId()).willReturn(expiredSessionId);

        given(sessionRepository.findFinishedSessionsOlderThan(any(Instant.class)))
            .willReturn(List.of())
            .willReturn(List.of(expiredSession));

        scheduler.cleanupOldHighlights();

        verify(highlightRepository).deleteAllBySessionIds(List.of(expiredSessionId));
        verify(segmentRepository).deleteAllBySessionIds(List.of(expiredSessionId));
        verify(sessionRepository).deleteExpiredSessions(any(Instant.class));
    }
}
