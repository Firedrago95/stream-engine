package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import io.slice.stream.apiserver.stream.fake.FakeViewMetricTimelineRepository;
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

    private FakeStringRedisTemplate redisTemplate;
    private FakeStreamRepository streamRepository;
    private FakeStreamSessionRepository sessionRepository;
    private FakeStreamSessionSegmentRepository segmentRepository;
    private FakeViewMetricTimelineRepository timelineRepository;

    private StreamSyncDirtyChecker dirtyChecker;
    private StreamSyncPersistenceService persistenceService;
    private StreamService streamService;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        streamRepository = new FakeStreamRepository();
        sessionRepository = new FakeStreamSessionRepository();
        segmentRepository = new FakeStreamSessionSegmentRepository();
        timelineRepository = new FakeViewMetricTimelineRepository();

        dirtyChecker = new StreamSyncDirtyChecker(redisTemplate);
        persistenceService = new StreamSyncPersistenceService(
            streamRepository,
            sessionRepository,
            segmentRepository,
            timelineRepository,
            new ConcurrentMapCacheManager("activeSessions")
        );

        streamService = new StreamService(dirtyChecker, persistenceService);
    }

    @Test
    void 신규_방송_목록을_동기화하면_DB에_정상_등록되고_스냅샷이_캐싱된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest req = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1000, "소통", startedAt);

        streamService.syncAll(List.of(req));

        Optional<StreamEntity> savedStream = streamRepository.findById("ch1");
        assertThat(savedStream).isPresent();
        assertThat(savedStream.get().getLiveTitle()).isEqualTo("제목1");

        Optional<StreamSessionEntity> savedSession = sessionRepository.findBySessionId("live1");
        assertThat(savedSession).isPresent();
    }

    @Test
    void 메타데이터가_변경되지_않은_두번째_동기화에서는_streams_테이블_갱신을_건너뛴다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest req = new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb.jpg", 1000, "소통", startedAt);

        streamService.syncAll(List.of(req));

        // DB에 저장된 상태를 임의로 다른 객체로 교체해둠 (dirty check 건너뛰는지 확인용)
        StreamEntity sentinel = new StreamEntity("ch1", "침착맨");
        sentinel.heartbeat("침착맨", "감시용제목", "thumb.jpg", "소통", 1000);
        streamRepository.upsertStream(sentinel, Instant.now());

        // 동일한 데이터로 다시 syncAll 호출 (Clean 상태)
        streamService.syncAll(List.of(req));

        // DB의 제목이 sentinel 제목 그대로 유지되어 있어야 함 (upsert 생략 증명)
        Optional<StreamEntity> current = streamRepository.findById("ch1");
        assertThat(current).isPresent();
        assertThat(current.get().getLiveTitle()).isEqualTo("감시용제목");

        // 하지만 타임라인은 누락 없이 2건 모두 쌓여야 함 (기능 100% 유지)
        assertThat(timelineRepository.findBySessionIdOrderByTimestampAsc("live1")).hasSize(2);
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
