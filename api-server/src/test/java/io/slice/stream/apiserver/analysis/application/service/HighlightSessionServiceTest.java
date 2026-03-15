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
    private final Duration cooldown = Duration.ofSeconds(90); // 90초 설정 반영

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(highlightSessionService, "leadingBuffer", leadingBuffer);
        ReflectionTestUtils.setField(highlightSessionService, "cooldown", cooldown);
        highlightSessionService.init(); // 필수: NMS 캐시 초기화
    }

    @Test
    void 첫_PEAK_신호가_오면_새로운_세션을_생성하고_캐시에_등록한다() {
        // given
        Instant now = Instant.now();
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "PEAK", now, 100L);
        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.empty());

        // when
        highlightSessionService.handleSignal(signal);

        // then
        ArgumentCaptor<HighlightEventEntity> captor = ArgumentCaptor.forClass(HighlightEventEntity.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPeakFirepower()).isEqualTo(100L);
    }

    @Test
    void 쿨다운_기간_내에_더_작은_PEAK가_오면_NMS가_작동하여_DB업데이트를_무시한다() {
        // given (캐시에 초기 피크 100 적재)
        Instant now = Instant.now();
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "PEAK", now, 100L));

        // when (90초 이내에 50짜리 더 작은 피크 발생)
        AnalysisSignal smallerSignal = AnalysisSignal.of(STREAM_ID, "PEAK", now.plusSeconds(10), 50L);
        highlightSessionService.handleSignal(smallerSignal);

        // then (최초 1회 외에는 DB 조회가 일어나지 않아야 함)
        verify(repository, times(1)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 쿨다운_기간_내에_더_큰_PEAK가_오면_세션을_연장하고_캐시를_갱신한다() {
        // given
        Instant now = Instant.now();
        HighlightEventEntity ongoingSession = new HighlightEventEntity(STREAM_ID, now, now, 100L);

        when(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING"))
            .thenReturn(Optional.of(ongoingSession));

        // 최초 피크 (100)
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "PEAK", now, 100L));

        // when (90초 이내에 더 큰 피크 150 발생)
        AnalysisSignal largerSignal = AnalysisSignal.of(STREAM_ID, "PEAK", now.plusSeconds(10), 150L);
        highlightSessionService.handleSignal(largerSignal);

        // then
        assertThat(ongoingSession.getPeakFirepower()).isEqualTo(150L);
        verify(repository, times(2)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }

    @Test
    void 쿨다운_기간_내에_NORMAL_신호가_오면_DB조회없이_무시한다() {
        // given (캐시에 피크 등록)
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "PEAK", Instant.now(), 100L));

        // when (쿨다운 기간 내 NORMAL 발생)
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "NORMAL", Instant.now().plusSeconds(10), 10L));

        // then (NORMAL 처리를 위한 DB 조회가 발생하지 않음)
        verify(repository, times(1)).findFirstByStreamIdAndStatusOrderByStartTimeDesc(any(), any());
    }
}
