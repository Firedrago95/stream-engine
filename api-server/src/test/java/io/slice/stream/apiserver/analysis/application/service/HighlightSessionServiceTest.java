package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.fake.FakeHighlightEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HighlightSessionServiceTest {

    private FakeHighlightEventRepository repository;
    private HighlightProperties properties;
    private HighlightSessionPersistenceService persistenceService;
    private HighlightSessionService highlightSessionService;

    private static final String STREAM_ID = "test-stream";

    @BeforeEach
    void setUp() {
        repository = new FakeHighlightEventRepository();
        properties = new HighlightProperties(
            Duration.ofSeconds(40),
            Duration.ofSeconds(10),
            Duration.ofSeconds(60),
            0.7,
            5,
            6,
            20,
            10,
            24,
            30,
            365
        );
        persistenceService = new HighlightSessionPersistenceService(repository);
        highlightSessionService = new HighlightSessionService(persistenceService, repository, properties);
        highlightSessionService.init();
    }

    @Test
    @DisplayName("첫 PEAK 신호가 오면 새로운 세션을 생성하고 저장소에 등록한다")
    void 첫_PEAK_신호가_오면_새로운_세션을_생성하고_캐시에_등록한다() {
        Instant now = Instant.now();
        long offsetMs = 3600000L;
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs);

        highlightSessionService.handleSignal(signal);

        Optional<HighlightEventEntity> sessionOpt =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING");
        assertThat(sessionOpt).isPresent();

        HighlightEventEntity saved = sessionOpt.get();
        assertThat(saved.getPeakFirepower()).isEqualTo(100L);
        assertThat(saved.getStartTimeOffset()).isEqualTo(3560000L);

        Instant streamStartedAt = now.minusMillis(offsetMs);
        assertThat(saved.getStartTime()).isEqualTo(streamStartedAt.plusMillis(3560000L));
    }

    @Test
    @DisplayName("방송 초반에 피크 발생 시 오프셋이 음수가 되지 않고 0으로 정렬된다")
    void 방송_초반에_피크_발생시_오프셋이_음수가_되지_않고_방송_시작시각으로_정렬된다() {
        Instant now = Instant.now();
        long offsetMs = 5000L;
        AnalysisSignal signal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs);

        highlightSessionService.handleSignal(signal);

        Optional<HighlightEventEntity> sessionOpt =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING");
        assertThat(sessionOpt).isPresent();

        HighlightEventEntity saved = sessionOpt.get();
        assertThat(saved.getStartTimeOffset()).isZero();

        Instant streamStartedAt = now.minusMillis(offsetMs);
        assertThat(saved.getStartTime()).isEqualTo(streamStartedAt);
    }

    @Test
    @DisplayName("쿨다운 기간 내에 더 작은 PEAK가 오면 NMS가 작동하여 세션을 업데이트하지 않는다")
    void 쿨다운_기간_내에_더_작은_PEAK가_오면_NMS가_작동하여_DB업데이트를_무시한다() {
        Instant now = Instant.now();
        long offsetMs = 3600000L;
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, offsetMs));

        AnalysisSignal smallerSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now.plusSeconds(10), 50L, offsetMs + 10000L);
        highlightSessionService.handleSignal(smallerSignal);

        HighlightEventEntity session = repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING").orElseThrow();
        assertThat(session.getLastPeakOffset()).isEqualTo(offsetMs);
        assertThat(session.getPeakFirepower()).isEqualTo(100L);
    }

    @Test
    @DisplayName("쿨다운 기간 내에 70% 이상의 여진이 오면 세션을 연장하고 피크 화력은 유지한다")
    void 쿨다운_기간_내에_70퍼센트_이상의_여진이_오면_버퍼를_연장하고_최대화력은_유지한다() {
        Instant now = Instant.now();
        long initialOffset = 3600000L;

        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, initialOffset));

        long after10SecOffset = initialOffset + 10000L;
        AnalysisSignal secondarySignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now.plusSeconds(10), 80L, after10SecOffset);
        highlightSessionService.handleSignal(secondarySignal);

        HighlightEventEntity session = repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING").orElseThrow();
        assertThat(session.getPeakFirepower()).isEqualTo(100L);
        assertThat(session.getLastPeakOffset()).isEqualTo(after10SecOffset);
    }

    @Test
    @DisplayName("쿨다운 기간 내에 NORMAL 신호가 오면 무시한다")
    void 쿨다운_기간_내에_NORMAL_신호가_오면_DB조회없이_무시한다() {
        Instant now = Instant.now();
        long initialOffset = 3600000L;
        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 100L, initialOffset));

        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "NORMAL", now.plusSeconds(10), 10L, initialOffset + 10000L));

        HighlightEventEntity session = repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING").orElseThrow();
        assertThat(session.getStatus()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("스케줄러 동작 시 3분 이상 방치된 좀비 세션을 찾아 강제 종료한다")
    void 스케줄러가_동작하면_3분이상_방치된_좀비세션을_찾아_종료한다() {
        Instant peakTime = Instant.now().minus(Duration.ofMinutes(4));
        long lastPeakOffset = 10000L;

        HighlightEventEntity zombieSession = new HighlightEventEntity(
            STREAM_ID, "sessionId", peakTime, lastPeakOffset, peakTime, lastPeakOffset, 100L
        );
        repository.save(zombieSession);

        highlightSessionService.cleanUpZombieSessions();

        assertThat(zombieSession.getStatus()).isEqualTo("FINISHED");
        long expectedEndTimeOffset = lastPeakOffset + properties.trailingBuffer().toMillis();
        assertThat(zombieSession.getEndTimeOffset()).isEqualTo(expectedEndTimeOffset);
    }

    @Test
    @DisplayName("화력이 최소 임계값 미만인 PEAK 신호는 세션을 생성하지 않고 무시된다")
    void 화력이_최소_임계값_미만인_PEAK_신호는_세션을_생성하지_않고_무시된다() {
        Instant now = Instant.now();
        AnalysisSignal weakSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", now, 4L, 3600000L);

        highlightSessionService.handleSignal(weakSignal);

        assertThat(repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(STREAM_ID, "ONGOING")).isEmpty();
    }

    @Test
    @DisplayName("쿨다운 60초 경과 후 NORMAL 신호 수신 시 진행 중인 세션이 정상 마감된다")
    void 쿨다운_60초_경과_후_NORMAL_신호가_오면_진행중인_세션이_정상적으로_마감된다() {
        Instant peakTime = Instant.now();
        long peakOffset = 3600000L;

        highlightSessionService.handleSignal(AnalysisSignal.of(STREAM_ID, "sessionId", "PEAK", peakTime, 100L, peakOffset));

        @SuppressWarnings("unchecked")
        Cache<String, Long> cache = (Cache<String, Long>) ReflectionTestUtils.getField(highlightSessionService, "nmsCache");
        if (cache != null) {
            cache.invalidateAll();
        }

        Instant normalTime = peakTime.plusSeconds(61);
        long normalOffset = peakOffset + 61000L;
        AnalysisSignal normalSignal = AnalysisSignal.of(STREAM_ID, "sessionId", "NORMAL", normalTime, 2L, normalOffset);
        highlightSessionService.handleSignal(normalSignal);

        List<HighlightEventEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        HighlightEventEntity session = all.get(0);
        assertThat(session.getStatus()).isEqualTo("FINISHED");
        assertThat(session.getEndTime()).isEqualTo(peakTime.plus(properties.trailingBuffer()));
        assertThat(session.getEndTimeOffset()).isEqualTo(peakOffset + properties.trailingBuffer().toMillis());
    }

    @Test
    @DisplayName("진행 중인 세션이 없는 스트림에 NORMAL 신호가 오면 DB 조회나 변경 없이 즉시 무시된다")
    void shouldIgnoreNormalSignalWhenNoOngoingSessionExists() {
        AnalysisSignal normalSignal = AnalysisSignal.of("unknown-stream", "sessionId", "NORMAL", Instant.now(), 2L, 10000L);

        highlightSessionService.handleSignal(normalSignal);

        assertThat(repository.findAll()).isEmpty();
    }
}
