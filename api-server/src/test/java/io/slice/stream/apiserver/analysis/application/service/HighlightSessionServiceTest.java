package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightSessionServiceTest {

    @Mock
    private JpaHighlightEventRepository repository;

    @InjectMocks
    private HighlightSessionService highlightSessionService;

    private static final String STREAM_ID = "test-stream";
    private final Duration leadingBuffer = Duration.ofSeconds(10);
    private final Duration trailingBuffer = Duration.ofSeconds(20);
    private final Duration cooldown = Duration.ofSeconds(90);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(highlightSessionService, "leadingBuffer", leadingBuffer);
        ReflectionTestUtils.setField(highlightSessionService, "trailingBuffer", trailingBuffer);
        ReflectionTestUtils.setField(highlightSessionService, "cooldown", cooldown);
        ReflectionTestUtils.setField(highlightSessionService, "extensionRatio", 0.7);
        highlightSessionService.init();
    }

    @Test
    void 첫_PEAK_신호가_오면_새로운_세션을_생성하고_캐시에_등록한다() {
        // given
        Instant now = Instant.now();
        long offsetMs = 3600000L; // 방송 시작 1시간 지점
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.empty());

        // when
        highlightSessionService.handleSignal(signal);

        // then
        ArgumentCaptor<HighlightEventEntity> captor = ArgumentCaptor.forClass(HighlightEventEntity.class);
        verify(repository, times(1)).save(captor.capture());

        HighlightEventEntity saved = captor.getValue();
        assertThat(saved.getPeakFirepower()).isEqualTo(100L);

        // VOD 시작 오프셋 계산: 1시간(3,600,000ms) - 10초(10,000ms) = 3,590,000ms
        assertThat(saved.getStartTimeOffset()).isEqualTo(3590000L);
    }

    @Test
    void 쿨다운_기간_내에_더_작은_PEAK가_오면_NMS가_작동하여_DB업데이트를_무시한다() {
        // given
        Instant now = Instant.now();
        long offsetMs = 3600000L;
        // 최초 피크 100 적재
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs));

        // when (10초 뒤 50짜리 더 작은 피크 발생)
        AnalysisSignal smallerSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now.plusSeconds(10), 50L, offsetMs + 10000L);
        highlightSessionService.handleSignal(smallerSignal);

        // then (최초 1회 외에는 DB 조회가 일어나지 않음)
        verify(repository, times(1)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 쿨다운_기간_내에_70퍼센트_이상의_여진이_오면_버퍼를_연장하고_최대화력은_유지한다() {
        // given
        Instant now = Instant.now();
        long initialOffset = 3600000L;

        HighlightEventEntity ongoingSession = new HighlightEventEntity(
            STREAM_ID,"sessionId", now, initialOffset - 10000L, now, initialOffset, 100L
        );

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.of(ongoingSession));

        // 최초 피크 (100)
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, initialOffset));

        // when (10초 뒤 화력 80의 여진 발생)
        long after10SecOffset = initialOffset + 10000L;
        AnalysisSignal secondarySignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now.plusSeconds(10), 80L, after10SecOffset);
        highlightSessionService.handleSignal(secondarySignal);

        // then
        assertThat(ongoingSession.getPeakFirepower()).isEqualTo(100L);
        assertThat(ongoingSession.getLastPeakOffset()).isEqualTo(after10SecOffset);
        verify(repository, times(2)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 쿨다운_기간_내에_NORMAL_신호가_오면_DB조회없이_무시한다() {
        // given
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", Instant.now(), 100L, 3600000L));

        // when (쿨다운 내 NORMAL 발생)
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "NORMAL", Instant.now().plusSeconds(10), 10L, 3610000L));

        // then
        verify(repository, times(1)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 스케줄러가_동작하면_3분이상_방치된_좀비세션을_찾아_종료한다() {
        // given
        Instant peakTime = Instant.now().minus(Duration.ofMinutes(4));
        long lastPeakOffset = 10000L;

        HighlightEventEntity zombieSession = new HighlightEventEntity(
            STREAM_ID, "sessionId", peakTime, lastPeakOffset, peakTime, lastPeakOffset, 100L
        );

        when(repository.findZombieSessions(any(Instant.class))).thenReturn(List.of(zombieSession));

        // when
        highlightSessionService.cleanUpZombieSessions();

        // then
        assertThat(zombieSession.getStatus()).isEqualTo("FINISHED");
        long expectedEndTimeOffset = lastPeakOffset + trailingBuffer.toMillis();
        assertThat(zombieSession.getEndTimeOffset()).isEqualTo(expectedEndTimeOffset);
    }
}
