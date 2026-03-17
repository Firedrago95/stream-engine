package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightCleanupSchedulerTest {

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaHighlightEventRepository highlightRepository;

    @InjectMocks
    private HighlightCleanupScheduler scheduler;

    @Test
    void 하루가_지난_종료된_세션들에_대해_청소_로직을_실행한다() {
        // given
        String sessionId = "old-session-id";
        StreamSessionEntity oldSession = mock(StreamSessionEntity.class);
        given(oldSession.getSessionId()).willReturn(sessionId);

        // 24시간 지난 세션 목록으로 반환되도록 설정
        given(sessionRepository.findFinishedSessionsOlderThan(any(Instant.class)))
            .willReturn(List.of(oldSession));

        // when
        scheduler.cleanupOldHighlights();

        // then
        // 1. 세션 조회 시 전달되는 시간이 현재로부터 약 24시간 전인지 확인 (Interaction 위주)
        verify(sessionRepository).findFinishedSessionsOlderThan(any(Instant.class));

        // 2. 해당 세션 ID로 deleteExceptTop10이 호출되었는지 확인
        verify(highlightRepository).deleteExceptTop(sessionId, 10);
    }
}
