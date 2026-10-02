package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.global.config.HighlightProperties;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightSessionServiceTest {

    @Mock
    private JpaHighlightEventRepository repository;

    @Spy
    private HighlightProperties properties = new HighlightProperties(
        Duration.ofSeconds(20),
        Duration.ofSeconds(10),
        Duration.ofSeconds(15),
        0.7,
        6,
        6,
        20,
        10,
        24,
        30,
        365
    );

    @InjectMocks
    private HighlightSessionService highlightSessionService;

    private static final String STREAM_ID = "test-stream";

    @BeforeEach
    void setUp() {
        highlightSessionService.init();
    }

    @Test
    void 첫_PEAK_신호가_오면_새로운_세션을_생성하고_캐시에_등록한다() {
        Instant now = Instant.now();
        long offsetMs = 3600000L;
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.empty());

        highlightSessionService.handleSignal(signal);

        ArgumentCaptor<HighlightEventEntity> captor = ArgumentCaptor.forClass(HighlightEventEntity.class);
        verify(repository, times(1)).save(captor.capture());

        HighlightEventEntity saved = captor.getValue();
        assertThat(saved.getPeakFirepower()).isEqualTo(100L);
        assertThat(saved.getStartTimeOffset()).isEqualTo(3580000L);

        Instant streamStartedAt = now.minusMillis(offsetMs);
        assertThat(saved.getStartTime()).isEqualTo(streamStartedAt.plusMillis(3580000L));
    }

    @Test
    void 방송_초반에_피크_발생시_오프셋이_음수가_되지_않고_방송_시작시각으로_정렬된다() {
        Instant now = Instant.now();
        long offsetMs = 5000L;
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.empty());

        highlightSessionService.handleSignal(signal);

        ArgumentCaptor<HighlightEventEntity> captor = ArgumentCaptor.forClass(HighlightEventEntity.class);
        verify(repository, times(1)).save(captor.capture());

        HighlightEventEntity saved = captor.getValue();
        assertThat(saved.getStartTimeOffset()).isEqualTo(0L);

        Instant streamStartedAt = now.minusMillis(offsetMs);
        assertThat(saved.getStartTime()).isEqualTo(streamStartedAt);
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
        long expectedEndTimeOffset = lastPeakOffset + properties.trailingBuffer().toMillis();
        assertThat(zombieSession.getEndTimeOffset()).isEqualTo(expectedEndTimeOffset);
    }

    @Test
    void 화력이_최소_임계값_미만인_PEAK_신호는_세션을_생성하지_않고_무시된다() {
        // given
        Instant now = Instant.now();
        AnalysisSignal weakSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 5L, 3600000L);

        // when
        highlightSessionService.handleSignal(weakSignal);

        // then
        verify(repository, never()).save(any());
        verify(repository, never()).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 쿨다운_15초_경과_후_NORMAL_신호가_오면_진행중인_세션이_정상적으로_마감된다() {
        // given
        Instant peakTime = Instant.now();
        long peakOffset = 3600000L;
        HighlightEventEntity ongoingSession = new HighlightEventEntity(
            STREAM_ID, "sessionId", peakTime, peakOffset - 20000L, peakTime, peakOffset, 100L
        );

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.of(ongoingSession));

        // 최초 피크 수신으로 캐시 등록
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", peakTime, 100L, peakOffset));

        // 15초 쿨다운 만료 시뮬레이션 (캐시 무효화)
        @SuppressWarnings("unchecked")
        Cache<String, Long> cache = (Cache<String, Long>) ReflectionTestUtils.getField(highlightSessionService, "nmsCache");
        if (cache != null) {
            cache.invalidateAll();
        }

        // when (16초 뒤 평시 화력 2의 NORMAL 신호 수신)
        Instant normalTime = peakTime.plusSeconds(16);
        long normalOffset = peakOffset + 16000L;
        AnalysisSignal normalSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "NORMAL", normalTime, 2L, normalOffset);
        highlightSessionService.handleSignal(normalSignal);

        // then
        assertThat(ongoingSession.getStatus()).isEqualTo("FINISHED");
        Instant expectedEndTime = peakTime.plus(properties.trailingBuffer());
        long expectedEndOffset = peakOffset + properties.trailingBuffer().toMillis();
        assertThat(ongoingSession.getEndTime()).isEqualTo(expectedEndTime);
        assertThat(ongoingSession.getEndTimeOffset()).isEqualTo(expectedEndOffset);
    }

    @Test
    void 쿨다운_15초_미경과시_NORMAL_신호가_오더라도_세션이_마감되지_않는다() {
        // given
        Instant peakTime = Instant.now();
        long peakOffset = 3600000L;
        HighlightEventEntity ongoingSession = new HighlightEventEntity(
            STREAM_ID, "sessionId", peakTime, peakOffset - 20000L, peakTime, peakOffset, 100L
        );

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.of(ongoingSession));

        // 최초 피크 수신으로 캐시 등록
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", peakTime, 100L, peakOffset));

        // 15초 쿨다운 만료 전 시뮬레이션: 캐시는 무효화되었으나 시간 임계치(15초) 미도달 (10초 경과)
        @SuppressWarnings("unchecked")
        Cache<String, Long> cache = (Cache<String, Long>) ReflectionTestUtils.getField(highlightSessionService, "nmsCache");
        if (cache != null) {
            cache.invalidateAll();
        }

        // when (10초 뒤 NORMAL 신호 수신 - threshold 15초 미도달)
        Instant normalTime = peakTime.plusSeconds(10);
        long normalOffset = peakOffset + 10000L;
        AnalysisSignal normalSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "NORMAL", normalTime, 2L, normalOffset);
        highlightSessionService.handleSignal(normalSignal);

        // then
        assertThat(ongoingSession.getStatus()).isEqualTo("ONGOING");
        assertThat(ongoingSession.getEndTime()).isNull();
    }
}
