package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

    @Mock
    private StreamerFollowerSnapshotRepository followerSnapshotRepository;

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
        30,
        365,
        365
    );

    @Test
    void 삼십일이_지난_하이라이트들에_대해_상위_십개를_제외한_벌크_압축을_실행한다() {
        scheduler.cleanupOldHighlights();

        verify(highlightRepository).compressOldHighlightsExceptTop(any(Instant.class), eq(10));
    }

    @Test
    void 일년이_지난_만료된_하이라이트_데이터를_영구_삭제한다() {
        scheduler.cleanupOldHighlights();

        verify(highlightRepository).deleteExpiredHighlights(any(Instant.class));
    }

    @Test
    void 일년이_지난_만료된_방송_세션과_카테고리_구간은_영구_삭제한다() {
        String expiredSessionId = "expired-1y-session-id";
        StreamSessionEntity expiredSession = mock(StreamSessionEntity.class);
        given(expiredSession.getSessionId()).willReturn(expiredSessionId);

        given(sessionRepository.findFinishedSessionsOlderThan(any(Instant.class)))
            .willReturn(List.of(expiredSession));

        scheduler.cleanupOldHighlights();

        verify(segmentRepository).deleteAllBySessionIds(List.of(expiredSessionId));
        verify(sessionRepository).deleteExpiredSessions(any(Instant.class));
        verify(followerSnapshotRepository).deleteExpiredSnapshots(any(LocalDate.class));
    }
}
