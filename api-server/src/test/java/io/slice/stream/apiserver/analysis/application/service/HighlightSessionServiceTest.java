package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Duration;
import java.time.Instant;
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
    private final Duration cooldown = Duration.ofSeconds(10);

    @BeforeEach
    void setUp() {
        // @Value 필드 수동 주입
        ReflectionTestUtils.setField(highlightSessionService, "leadingBuffer", leadingBuffer);
        ReflectionTestUtils.setField(highlightSessionService, "cooldown", cooldown);
    }

    @Test
    void PEAK_신호가_오고_진행중인_세션이_없으면_새로운_세션을_생성한다() {
        // given
        Instant now = Instant.now();
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "PEAK", now, 100L);
        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).thenReturn(Optional.empty());

        // when
        highlightSessionService.handleSignal(signal);

        // then
        ArgumentCaptor<HighlightEventEntity> captor = ArgumentCaptor.forClass(HighlightEventEntity.class);
        verify(repository, times(1)).save(captor.capture());

        HighlightEventEntity savedSession = captor.getValue();
        assertThat(savedSession.getStreamId()).isEqualTo(STREAM_ID);
        // 시작 시간이 leadingBuffer 만큼 앞당겨졌는지 확인
        assertThat(savedSession.getStartTime()).isEqualTo(now.minus(leadingBuffer));
        assertThat(savedSession.getPeakFirepower()).isEqualTo(100L);
        assertThat(savedSession.getStatus()).isEqualTo("ONGOING");
    }

    @Test
    void PEAK_신호가_오고_진행중인_세션이_있으면_세션을_연장한다() {
        // given
        Instant startTime = Instant.now().minusSeconds(30);
        Instant lastPeakTime = Instant.now().minusSeconds(5);
        HighlightEventEntity ongoingSession = new HighlightEventEntity(STREAM_ID, startTime, lastPeakTime, 50L);

        Instant now = Instant.now();
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "PEAK", now, 150L);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).thenReturn(Optional.of(ongoingSession));

        // when
        highlightSessionService.handleSignal(signal);

        // then
        verify(repository, never()).save(any()); // 연장은 영속성 컨텍스트(Dirty Checking)에 의존하므로 save 호출 안함
        assertThat(ongoingSession.getPeakFirepower()).isEqualTo(150L); // 최고 화력 갱신 확인
        assertThat(ongoingSession.getLastPeakTime()).isEqualTo(now); // 마지막 피크 시간 갱신 확인
        assertThat(ongoingSession.getStatus()).isEqualTo("ONGOING"); // 상태 유지 확인
    }

    @Test
    void NORMAL_신호가_오고_유예기간이_지났으면_세션을_종료한다() {
        // given
        Instant now = Instant.now();
        Instant lastPeakTime = now.minus(cooldown).minusSeconds(1); // 쿨다운 + 1초 경과 (11초 전)
        HighlightEventEntity ongoingSession = new HighlightEventEntity(STREAM_ID, now.minusSeconds(60), lastPeakTime, 200L);

        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "NORMAL", now, 10L);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).thenReturn(Optional.of(ongoingSession));

        // when
        highlightSessionService.handleSignal(signal);

        // then
        assertThat(ongoingSession.getStatus()).isEqualTo("FINISHED");
        assertThat(ongoingSession.getEndTime()).isEqualTo(lastPeakTime.plus(cooldown));
    }

    @Test
    void NORMAL_신호가_오고_유예기간이_지나지_않았으면_세션을_유지한다() {
        // given
        Instant now = Instant.now();
        Instant lastPeakTime = now.minus(cooldown).plusSeconds(2); // 쿨다운 이내 (8초 전)
        HighlightEventEntity ongoingSession = new HighlightEventEntity(STREAM_ID, now.minusSeconds(60), lastPeakTime, 200L);

        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "NORMAL", now, 10L);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).thenReturn(Optional.of(ongoingSession));

        // when
        highlightSessionService.handleSignal(signal);

        // then
        assertThat(ongoingSession.getStatus()).isEqualTo("ONGOING");
        assertThat(ongoingSession.getEndTime()).isNull();
    }

    @Test
    void NORMAL_신호가_오고_진행중인_세션이_없으면_아무_동작도_하지_않는다() {
        // given
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "NORMAL", Instant.now(), 10L);
        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).thenReturn(Optional.empty());

        // when
        highlightSessionService.handleSignal(signal);

        // then
        verify(repository, never()).save(any());
    }
}
