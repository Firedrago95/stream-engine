package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
}
