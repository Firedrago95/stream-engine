package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.fake.FakeViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSyncPersistenceServiceTest {

    private FakeStreamRepository streamRepository;
    private FakeStreamSessionRepository sessionRepository;
    private FakeStreamSessionSegmentRepository segmentRepository;
    private FakeViewMetricTimelineRepository timelineRepository;
    private ConcurrentMapCacheManager cacheManager;
    private StreamSyncPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        streamRepository = new FakeStreamRepository();
        sessionRepository = new FakeStreamSessionRepository();
        segmentRepository = new FakeStreamSessionSegmentRepository();
        timelineRepository = new FakeViewMetricTimelineRepository();
        cacheManager = new ConcurrentMapCacheManager("activeSessions");

        persistenceService = new StreamSyncPersistenceService(
            streamRepository,
            sessionRepository,
            segmentRepository,
            timelineRepository,
            cacheManager
        );
    }

    private void invokePersist(List<StreamSyncRequest> allRequests) {
        Map<String, StreamSyncRequest> map = allRequests.stream()
            .collect(Collectors.toMap(StreamSyncRequest::streamId, Function.identity(), (a, b) -> b));
        persistenceService.persistSync(map, Instant.now());
    }

    @Test
    void 인입된_방송_목록이_DB에_정상_batchUpsert_되어야_한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 1000, "소통", startedAt);

        invokePersist(List.of(request));

        Optional<StreamEntity> saved = streamRepository.findById("ch1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStreamId()).isEqualTo("ch1");
        assertThat(saved.get().isLive()).isTrue();
    }

    @Test
    void 빈_목록이_인입되면_streams_테이블_업서트는_수행되지_않아야_한다() {
        invokePersist(List.of());

        assertThat(streamRepository.findById("ch1")).isEmpty();
    }

    @Test
    void 활성_세션이_있는_경우_시청자수_타임라인을_적재하고_세션의_피크_시청자수를_갱신한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity session = new StreamSessionEntity("ch1", "live1", "제목", "소통", Instant.now().minusSeconds(120));
        sessionRepository.addSession(session);

        invokePersist(List.of(request));

        assertThat(session.getPeakViewers()).isEqualTo(3500);

        List<ViewMetricTimelineEntity> timelines = timelineRepository.findBySessionIdOrderByTimestampAsc("live1");
        assertThat(timelines).hasSize(1);
        assertThat(timelines.get(0).getStreamId()).isEqualTo("ch1");
        assertThat(timelines.get(0).getViewerCount()).isEqualTo(3500);
    }

    @Test
    void 활성_세션이_없는_경우_신규_세션과_초기_세그먼트를_벌크_생성하고_타임라인을_적재한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);

        invokePersist(List.of(request));

        Optional<StreamSessionEntity> createdSession = sessionRepository.findBySessionId("live1");
        assertThat(createdSession).isPresent();
        assertThat(createdSession.get().getStreamId()).isEqualTo("ch1");
        assertThat(createdSession.get().getTitle()).isEqualTo("제목");
        assertThat(createdSession.get().getCategoryName()).isEqualTo("소통");
        assertThat(createdSession.get().getStartedAt()).isEqualTo(startedAt);

        Optional<StreamSessionSegmentEntity> createdSegment = segmentRepository.findActiveSegment("live1");
        assertThat(createdSegment).isPresent();
        assertThat(createdSegment.get().getStreamId()).isEqualTo("ch1");

        List<ViewMetricTimelineEntity> timelines = timelineRepository.findBySessionIdOrderByTimestampAsc("live1");
        assertThat(timelines).hasSize(1);
        assertThat(timelines.get(0).getViewerCount()).isEqualTo(3500);
    }

    @Test
    void 종료된_세션이_DB에_존재하고_활성_세그먼트가_남아있는_경우_재활성화만_수행한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity existingSession = new StreamSessionEntity("ch1", "live1", "제목", "소통", startedAt);
        existingSession.finishSession(Instant.now(), 1000);
        assertThat(existingSession.getEndedAt()).isNotNull();

        StreamSessionSegmentEntity existingSegment = new StreamSessionSegmentEntity("ch1", "live1", "제목", "소통", startedAt, 0L);

        sessionRepository.addSession(existingSession);
        segmentRepository.addSegment(existingSegment);

        invokePersist(List.of(request));

        assertThat(existingSession.getEndedAt()).isNull();
        assertThat(existingSession.getPeakViewers()).isEqualTo(3500);
    }

    @Test
    void 활성_세션의_liveId가_요청의_liveId와_다른_경우_이전_세션을_평균시청자수와_함께_종료하고_신규_세션을_생성한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live2", "침착맨", "새 방송", "thumb.jpg", 4000, "게임", startedAt);
        StreamSessionEntity oldActiveSession = new StreamSessionEntity("ch1", "live1", "이전 방송", "소통", startedAt.minusSeconds(3600));

        sessionRepository.addSession(oldActiveSession);
        timelineRepository.setAverageViewerCount("live1", 2500.0);
        timelineRepository.setPeakViewerCount("live1", 3000);

        invokePersist(List.of(request));

        assertThat(oldActiveSession.getEndedAt()).isNotNull();
        assertThat(oldActiveSession.getAverageViewerCount()).isEqualTo(2500);
        assertThat(oldActiveSession.getPeakViewers()).isEqualTo(3000);

        Optional<StreamSessionEntity> newSession = sessionRepository.findBySessionId("live2");
        assertThat(newSession).isPresent();
        assertThat(newSession.get().getTitle()).isEqualTo("새 방송");
    }

    @Test
    void 종료된_세션이_DB에_존재하고_활성_세그먼트가_없는_경우_재활성화하고_치지직_시작시각_기준의_신규_세그먼트를_생성한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant currentTime = Instant.parse("2026-02-13T12:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "소통 방송", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity existingSession = new StreamSessionEntity("ch1", "live1", "소통 방송", "소통", startedAt);
        existingSession.finishSession(Instant.parse("2026-02-13T11:00:00Z"), 2000);

        StreamSessionSegmentEntity oldClosedSegment = new StreamSessionSegmentEntity(
            "ch1", "live1", "소통 방송", "소통", startedAt, 0L
        );
        oldClosedSegment.endSegment(Instant.parse("2026-02-13T11:00:00Z"), 3600000L);

        sessionRepository.addSession(existingSession);
        segmentRepository.addSegment(oldClosedSegment);

        Map<String, StreamSyncRequest> map = Map.of("ch1", request);
        persistenceService.persistSync(map, currentTime);

        assertThat(existingSession.getEndedAt()).isNull();

        Optional<StreamSessionSegmentEntity> activeSegment = segmentRepository.findActiveSegment("live1");
        assertThat(activeSegment).isPresent();
        assertThat(activeSegment.get().getStartedAt()).isEqualTo(startedAt);
        assertThat(activeSegment.get().getStartOffsetMs()).isEqualTo(7200000L);
    }
}
