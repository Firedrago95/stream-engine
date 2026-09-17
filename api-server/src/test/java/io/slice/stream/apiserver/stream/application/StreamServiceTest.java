package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaStreamSessionSegmentRepository segmentRepository;

    @Mock
    private JpaViewMetricTimelineRepository timelineRepository;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private StreamService streamService;

    @Captor
    private ArgumentCaptor<StreamEntity> streamCaptor;

    @Captor
    private ArgumentCaptor<List<ViewMetricTimelineEntity>> timelineCaptor;

    @Captor
    private ArgumentCaptor<List<StreamSessionEntity>> sessionListCaptor;

    @Captor
    private ArgumentCaptor<List<StreamSessionSegmentEntity>> segmentListCaptor;

    @Test
    void 방송_목록을_동기화하면_DB에_upsert_되어야_한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 1000, "소통", startedAt);

        streamService.syncAll(List.of(request));

        then(streamRepository).should().upsertStream(streamCaptor.capture(), any(Instant.class));

        StreamEntity savedEntity = streamCaptor.getValue();
        assertThat(savedEntity.getStreamId()).isEqualTo("ch1");
        assertThat(savedEntity.isLive()).isTrue();
    }

    @Test
    void 배치_내에_중복된_채널_ID가_있으면_최신_데이터로_한번만_upsert_되어야_한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest oldRequest = new StreamSyncRequest("ch1", "live1", "침착맨", "옛날 제목", "old.jpg", 1000, "소통", startedAt);
        StreamSyncRequest newRequest = new StreamSyncRequest("ch1", "live1", "침착맨", "새 제목", "new.jpg", 2000, "게임", startedAt);

        streamService.syncAll(List.of(oldRequest, newRequest));

        then(streamRepository).should(times(1)).upsertStream(streamCaptor.capture(), any(Instant.class));

        StreamEntity updatedEntity = streamCaptor.getValue();
        assertThat(updatedEntity.getLiveTitle()).isEqualTo("새 제목");
        assertThat(updatedEntity.getCategoryName()).isEqualTo("게임");
    }

    @Test
    void 활성_세션이_있는_경우_시청자수_타임라인을_적재하고_세션의_피크_시청자수를_갱신한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity session = new StreamSessionEntity("ch1", "live1", "제목", "소통", Instant.now().minusSeconds(120));

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of(session));

        streamService.syncAll(List.of(request));

        assertThat(session.getPeakViewers()).isEqualTo(3500);

        then(timelineRepository).should().saveAll(timelineCaptor.capture());
        List<ViewMetricTimelineEntity> savedTimeline = timelineCaptor.getValue();
        assertThat(savedTimeline).hasSize(1);
        assertThat(savedTimeline.get(0).getStreamId()).isEqualTo("ch1");
        assertThat(savedTimeline.get(0).getSessionId()).isEqualTo("live1");
        assertThat(savedTimeline.get(0).getViewerCount()).isEqualTo(3500);
        then(sessionRepository).should(never()).saveAll(any());
        then(segmentRepository).should(never()).saveAll(any());
    }

    @Test
    void 활성_세션이_없는_경우_신규_세션과_초기_세그먼트를_벌크_생성하고_타임라인을_적재한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of());

        streamService.syncAll(List.of(request));

        then(streamRepository).should().upsertStream(streamCaptor.capture(), any(Instant.class));

        then(sessionRepository).should().saveAll(sessionListCaptor.capture());
        List<StreamSessionEntity> savedSessions = sessionListCaptor.getValue();
        assertThat(savedSessions).hasSize(1);
        assertThat(savedSessions.get(0).getStreamId()).isEqualTo("ch1");
        assertThat(savedSessions.get(0).getSessionId()).isEqualTo("live1");
        assertThat(savedSessions.get(0).getTitle()).isEqualTo("제목");
        assertThat(savedSessions.get(0).getCategoryName()).isEqualTo("소통");
        assertThat(savedSessions.get(0).getStartedAt()).isEqualTo(startedAt);

        then(segmentRepository).should().saveAll(segmentListCaptor.capture());
        List<StreamSessionSegmentEntity> savedSegments = segmentListCaptor.getValue();
        assertThat(savedSegments).hasSize(1);
        assertThat(savedSegments.get(0).getStreamId()).isEqualTo("ch1");
        assertThat(savedSegments.get(0).getSessionId()).isEqualTo("live1");

        then(timelineRepository).should().saveAll(timelineCaptor.capture());
        List<ViewMetricTimelineEntity> savedTimeline = timelineCaptor.getValue();
        assertThat(savedTimeline).hasSize(1);
        assertThat(savedTimeline.get(0).getStreamId()).isEqualTo("ch1");
        assertThat(savedTimeline.get(0).getSessionId()).isEqualTo("live1");
        assertThat(savedTimeline.get(0).getViewerCount()).isEqualTo(3500);
    }

    @Test
    void 종료된_세션이_DB에_존재하고_활성_세그먼트가_남아있는_경우_재활성화만_수행한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity existingSession = new StreamSessionEntity("ch1", "live1", "제목", "소통", startedAt);
        existingSession.finishSession(Instant.now(), 1000);
        assertThat(existingSession.getEndedAt()).isNotNull();

        StreamSessionSegmentEntity existingSegment = new StreamSessionSegmentEntity("ch1", "live1", "제목", "소통", startedAt, 0L);

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of());
        given(sessionRepository.findAllBySessionIdIn(List.of("live1")))
            .willReturn(List.of(existingSession));
        given(segmentRepository.findAllActiveSegments(List.of("live1")))
            .willReturn(List.of(existingSegment));

        streamService.syncAll(List.of(request));

        assertThat(existingSession.getEndedAt()).isNull();
        assertThat(existingSession.getPeakViewers()).isEqualTo(3500);
        then(sessionRepository).should(never()).saveAll(any());
        then(segmentRepository).should(never()).saveAll(any());
        then(timelineRepository).should().saveAll(timelineCaptor.capture());
    }

    @Test
    void 활성_세션의_liveId가_요청의_liveId와_다른_경우_이전_세션을_평균시청자수와_함께_종료하고_신규_세션을_생성한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live2", "침착맨", "새 방송", "thumb.jpg", 4000, "게임", startedAt);
        StreamSessionEntity oldActiveSession = new StreamSessionEntity("ch1", "live1", "이전 방송", "소통", startedAt.minusSeconds(3600));

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of(oldActiveSession));
        given(sessionRepository.findAllBySessionIdIn(List.of("live2")))
            .willReturn(List.of());
        given(timelineRepository.findAverageViewerCountBySessionId("live1"))
            .willReturn(2500.0);
        given(timelineRepository.findPeakViewerCountBySessionId("live1"))
            .willReturn(3000);

        streamService.syncAll(List.of(request));

        assertThat(oldActiveSession.getEndedAt()).isNotNull();
        assertThat(oldActiveSession.getAverageViewerCount()).isEqualTo(2500);
        assertThat(oldActiveSession.getPeakViewers()).isEqualTo(3000);

        then(sessionRepository).should().saveAll(sessionListCaptor.capture());
        List<StreamSessionEntity> savedSessions = sessionListCaptor.getValue();
        assertThat(savedSessions).hasSize(1);
        assertThat(savedSessions.get(0).getSessionId()).isEqualTo("live2");

        then(segmentRepository).should().saveAll(segmentListCaptor.capture());
        List<StreamSessionSegmentEntity> savedSegments = segmentListCaptor.getValue();
        assertThat(savedSegments).hasSize(1);
        assertThat(savedSegments.get(0).getSessionId()).isEqualTo("live2");
    }

    @Test
    void 마지막_업데이트가_6분이_지난_스트림은_방종으로_감지되어_세션이_마감되고_스트림이_오프라인으로_전환된다() {
        Instant now = Instant.parse("2026-02-13T10:10:00Z");
        Instant lastUpdate = Instant.parse("2026-02-13T10:02:00Z"); // 8분 전 업데이트

        StreamSyncRequest activeRequest = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", now);
        StreamSessionEntity activeSession = new StreamSessionEntity("ch1", "live1", "제목", "소통", now.minusSeconds(600));

        StreamSessionEntity offlineSession = new StreamSessionEntity("ch_offline", "live_offline", "방종방송", "종합게임", lastUpdate.minusSeconds(3600));
        StreamEntity offlineStreamEntity = new StreamEntity("ch_offline", "오프라인스트리머");
        offlineStreamEntity.heartbeat("오프라인스트리머", "방종방송", "thumb.jpg", "종합게임", 500);
        // Reflection or setter not needed if lastUpdate is set, but StreamEntity sets Instant.now() on heartbeat.
        // We can mock streamRepository.findAllByStreamIdIn
        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of(activeSession));
        given(sessionRepository.findSessionsToClose(any(Instant.class)))
            .willReturn(List.of(offlineSession));
        given(streamRepository.findAllByStreamIdIn(List.of("ch_offline")))
            .willReturn(List.of(offlineStreamEntity));
        given(timelineRepository.findAverageViewerCountBySessionId("live_offline"))
            .willReturn(1234.0);
        given(timelineRepository.findPeakViewerCountBySessionId("live_offline"))
            .willReturn(2000);

        streamService.syncAll(List.of(activeRequest));

        assertThat(offlineSession.getEndedAt()).isNotNull();
        assertThat(offlineSession.getAverageViewerCount()).isEqualTo(1234);
        assertThat(offlineSession.getPeakViewers()).isEqualTo(2000);

        then(streamRepository).should().markAllOfflineBefore(any(Instant.class));
    }

    @Test
    void 종료된_세션_재활성화_시_활성_세그먼트가_없으면_새_세그먼트를_생성한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        StreamSessionEntity existingSession = new StreamSessionEntity("ch1", "live1", "제목", "소통", startedAt);
        existingSession.finishSession(Instant.now(), 1000);

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of());
        given(sessionRepository.findAllBySessionIdIn(List.of("live1")))
            .willReturn(List.of(existingSession));
        given(segmentRepository.findAllActiveSegments(List.of("live1")))
            .willReturn(List.of());

        streamService.syncAll(List.of(request));

        assertThat(existingSession.getEndedAt()).isNull();
        then(sessionRepository).should(never()).saveAll(any());
        then(segmentRepository).should().saveAll(segmentListCaptor.capture());
        List<StreamSessionSegmentEntity> savedSegments = segmentListCaptor.getValue();
        assertThat(savedSegments).hasSize(1);
        assertThat(savedSegments.get(0).getSessionId()).isEqualTo("live1");
    }

    @Test
    void 신규_세션_생성_시_activeSessions_캐시를_무효화한다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamSyncRequest request = new StreamSyncRequest("ch1", "live1", "침착맨", "제목", "thumb.jpg", 3500, "소통", startedAt);
        Cache activeSessionsCache = mock(Cache.class);

        given(sessionRepository.findAllActiveSessions(List.of("ch1")))
            .willReturn(List.of());
        given(sessionRepository.findAllBySessionIdIn(List.of("live1")))
            .willReturn(List.of());
        given(cacheManager.getCache("activeSessions"))
            .willReturn(activeSessionsCache);

        streamService.syncAll(List.of(request));

        then(activeSessionsCache).should().evict("ch1");
    }
}
