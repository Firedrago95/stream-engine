package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class HighlightQueryServiceTest {

    @Mock
    private JpaHighlightEventRepository highlightRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Spy
    private HighlightProperties properties = new HighlightProperties(
        Duration.ofSeconds(20), // leadingBuffer
        Duration.ofSeconds(5),  // trailingBuffer
        Duration.ofSeconds(90), // cooldown
        0.7,                    // extensionRatio
        5,                      // minimum
        6,                      // realtimeLimit
        20,                     // historyDisplayLimit
        10,                     // cleanupRetentionLimit
        24
    );

    @InjectMocks
    private HighlightQueryService highlightQueryService;

    @Test
    void 특정_과거_세션_ID가_주어지면_해당_세션의_하이라이트를_조회한다() {
        // given
        String streamId = "stream-123";
        String sessionId = "past-session";
        Instant start = Instant.parse("2026-03-04T10:00:00Z");

        HighlightEventEntity entity = new HighlightEventEntity(
            streamId, sessionId, start, 3600000L, start, 3600000L, 500L
        );
        entity.finish(start.plusSeconds(60), 3660000L);

        given(highlightRepository.findAllByStreamIdAndSessionId(streamId, sessionId))
            .willReturn(List.of(entity));

        // when
        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, sessionId);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).durationSeconds()).isEqualTo(60L);

        // 과거 세션이므로 ActiveSession을 조회하지 않아야 함
        verify(sessionRepository, never()).findActiveSession(any());
        verify(highlightRepository).findAllByStreamIdAndSessionId(streamId, sessionId);
    }

    @Test
    void 실시간_조회_시_진행중인_방송이_있으면_현재_세션의_하이라이트를_반환한다() {
        // given
        String streamId = "stream-123";
        String activeSessionId = "active-session";
        Instant start = Instant.now();

        StreamSessionEntity activeSession = new StreamSessionEntity(
            streamId, activeSessionId, "방제", "카테고리", start
        );

        HighlightEventEntity entity = new HighlightEventEntity(
            streamId, activeSessionId, start, 0L, start, 0L, 200L
        );

        given(sessionRepository.findActiveSession(streamId))
            .willReturn(Optional.of(activeSession));
        given(highlightRepository.findAllByStreamIdAndSessionId(streamId, activeSessionId))
            .willReturn(List.of(entity));

        // when (null 이거나 "realtime" 일 때 동일하게 동작해야 함)
        List<HighlightResponse> resultWithNull = highlightQueryService.getHighlightsBySessionId(streamId, null);
        List<HighlightResponse> resultWithRealtime = highlightQueryService.getHighlightsBySessionId(streamId, "realtime");

        // then
        assertThat(resultWithNull).hasSize(1);
        assertThat(resultWithRealtime).hasSize(1);

        verify(sessionRepository, times(2)).findActiveSession(streamId);
        verify(highlightRepository, times(2)).findAllByStreamIdAndSessionId(streamId, activeSessionId);
    }

    @Test
    void 실시간_조회_시_진행중인_방송이_없으면_빈_리스트를_반환한다() {
        // given
        String streamId = "stream-123";

        // 진행 중인 방송이 없음
        given(sessionRepository.findActiveSession(streamId))
            .willReturn(Optional.empty());

        // when
        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, "realtime");

        // then
        assertThat(results).isEmpty();

        verify(sessionRepository).findActiveSession(streamId);
        verify(highlightRepository, never()).findAllByStreamIdAndSessionId(any(), any());
    }
}
