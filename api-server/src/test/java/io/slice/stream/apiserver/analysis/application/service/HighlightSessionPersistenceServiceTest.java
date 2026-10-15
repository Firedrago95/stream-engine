package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.stream.fake.FakeHighlightEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HighlightSessionPersistenceServiceTest {

    private FakeHighlightEventRepository repository;
    private HighlightSessionPersistenceService persistenceService;

    private static final String STREAM_ID = "test-stream";

    @BeforeEach
    void setUp() {
        repository = new FakeHighlightEventRepository();
        persistenceService = new HighlightSessionPersistenceService(repository);
    }

    @Test
    @DisplayName("진행 중인 세션이 없을 때 피크 신호가 오면 새 하이라이트 세션을 시작한다")
    void shouldStartNewSessionWhenNoOngoingSession() {
        Instant now = Instant.now();
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "session-1", "PEAK", now, 150L, 50000L);

        persistenceService.updateDbSessionForPeak(signal, Duration.ofSeconds(10));

        Optional<HighlightEventEntity> sessionOpt =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING");
        assertThat(sessionOpt).isPresent();
        HighlightEventEntity session = sessionOpt.get();
        assertThat(session.getPeakFirepower()).isEqualTo(150L);
        assertThat(session.getStartTimeOffset()).isEqualTo(40000L);
    }

    @Test
    @DisplayName("진행 중인 세션이 있을 때 피크 신호가 오면 세션을 연장한다")
    void shouldExtendOngoingSessionOnPeak() {
        Instant now = Instant.now();
        AnalysisSignal firstSignal = AnalysisSignal.of(STREAM_ID, "session-1", "PEAK", now, 100L, 50000L);
        persistenceService.updateDbSessionForPeak(firstSignal, Duration.ofSeconds(10));

        AnalysisSignal nextSignal = AnalysisSignal.of(STREAM_ID, "session-1", "PEAK", now.plusSeconds(5), 180L, 55000L);
        persistenceService.updateDbSessionForPeak(nextSignal, Duration.ofSeconds(10));

        HighlightEventEntity session =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING").orElseThrow();
        assertThat(session.getPeakFirepower()).isEqualTo(180L);
        assertThat(session.getLastPeakOffset()).isEqualTo(55000L);
    }

    @Test
    @DisplayName("쿨다운 임계 시간을 넘은 정상 신호 수신 시 세션을 정상 종료한다")
    void shouldFinishSessionWhenEligible() {
        Instant peakTime = Instant.now();
        AnalysisSignal firstSignal = AnalysisSignal.of(STREAM_ID, "session-1", "PEAK", peakTime, 100L, 50000L);
        persistenceService.updateDbSessionForPeak(firstSignal, Duration.ofSeconds(10));

        Instant normalTime = peakTime.plusSeconds(65);
        AnalysisSignal normalSignal = AnalysisSignal.of(STREAM_ID, "session-1", "NORMAL", normalTime, 10L, 115000L);

        boolean finished = persistenceService.finishSessionIfEligible(
            normalSignal, Duration.ofSeconds(60), Duration.ofSeconds(20)
        );

        assertThat(finished).isTrue();
        assertThat(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).isEmpty();
    }
}
