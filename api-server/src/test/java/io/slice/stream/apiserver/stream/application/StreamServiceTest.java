package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.fake.FakeAnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamServiceTest {

    private FakeStreamRepository streamRepository;
    private FakeStreamSessionRepository sessionRepository;
    private FakeStreamSessionSegmentRepository segmentRepository;
    private FakeAnalysisSignalSummaryJdbcRepository summaryRepository;

    private StreamSyncPersistenceService persistenceService;
    private StreamService streamService;

    @BeforeEach
    void setUp() {
        streamRepository = new FakeStreamRepository();
        sessionRepository = new FakeStreamSessionRepository();
        segmentRepository = new FakeStreamSessionSegmentRepository();
        summaryRepository = new FakeAnalysisSignalSummaryJdbcRepository();

        persistenceService = new StreamSyncPersistenceService(
            streamRepository,
            sessionRepository,
            segmentRepository,
            summaryRepository,
            new ConcurrentMapCacheManager("activeSessions")
        );

        streamService = new StreamService(persistenceService);
    }

    @Test
    void 신규_방송_목록을_동기화하면_DB에_정상_등록된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest req = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1000, "소통", startedAt);

        streamService.syncAll(List.of(req));

        Optional<StreamEntity> savedStream = streamRepository.findById("ch1");
        assertThat(savedStream).isPresent();
        assertThat(savedStream.get().getLiveTitle()).isEqualTo("제목1");
        assertThat(savedStream.get().getConcurrentUserCount()).isEqualTo(1000);

        Optional<StreamSessionEntity> savedSession = sessionRepository.findBySessionId("live1");
        assertThat(savedSession).isPresent();
    }

    @Test
    void 연속_동기화_시_최신_시청자수와_lastUpdateAt이_누락_없이_갱신된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest req1 = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1000, "소통", startedAt);
        streamService.syncAll(List.of(req1));

        // 시청자 수가 1000명에서 1050명으로 약간 변동한 경우 (과거 dirty check 기준 미달이어도 정상 반영 검증)
        StreamSyncRequest req2 = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1050, "소통", startedAt);
        streamService.syncAll(List.of(req2));

        Optional<StreamEntity> current = streamRepository.findById("ch1");
        assertThat(current).isPresent();
        assertThat(current.get().getConcurrentUserCount()).isEqualTo(1050);
        assertThat(current.get().isLive()).isTrue();

        Optional<StreamSessionEntity> session = sessionRepository.findBySessionId("live1");
        assertThat(session).isPresent();
        assertThat(session.get().getViewerSampleCount()).isEqualTo(2);
        assertThat(session.get().getAverageViewerCount()).isEqualTo(1025);
    }

    @Test
    void 메타데이터가_변경된_경우에는_정상적으로_DB에_새_정보가_반영된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest req = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1000, "소통", startedAt);
        streamService.syncAll(List.of(req));

        // 방제 변경
        StreamSyncRequest updatedReq = new StreamSyncRequest("ch1", "live1", "침착맨", "변경된 제목", "thumb.jpg", 1000, "게임", startedAt);
        streamService.syncAll(List.of(updatedReq));

        Optional<StreamEntity> current = streamRepository.findById("ch1");
        assertThat(current).isPresent();
        assertThat(current.get().getLiveTitle()).isEqualTo("변경된 제목");
        assertThat(current.get().getCategoryName()).isEqualTo("게임");
    }

    @Test
    void 중복_인입된_채널은_최신_정보로_단일화되어_동기화된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest oldReq = new StreamSyncRequest("ch1", "live1", "침착맨", "옛날 제목", "thumb.jpg", 1000, "소통", startedAt);
        StreamSyncRequest newReq = new StreamSyncRequest("ch1", "live1", "침착맨", "새 제목", "thumb.jpg", 1000, "소통", startedAt);

        streamService.syncAll(List.of(oldReq, newReq));

        Optional<StreamEntity> saved = streamRepository.findById("ch1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getLiveTitle()).isEqualTo("새 제목");
    }

    @Test
    void null이나_빈_목록이_인입되면_아무_작업도_하지_않는다() {
        streamService.syncAll(null);
        streamService.syncAll(List.of());

        assertThat(streamRepository.findAll()).isEmpty();
    }
}
