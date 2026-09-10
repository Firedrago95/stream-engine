package io.slice.stream.apiserver.stream.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "view_metric_timelines",
    indexes = {
        @Index(name = "idx_viewer_metric_session", columnList = "session_id, timestamp DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViewMetricTimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "viewer_count", nullable = false)
    private int viewerCount;

    public ViewMetricTimelineEntity(String streamId, String sessionId, Instant timestamp, int viewerCount) {
        this(null, streamId, sessionId, timestamp, viewerCount);
    }

    public ViewMetricTimelineEntity(Long id, String streamId, String sessionId, Instant timestamp, int viewerCount) {
        this.id = id;
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.viewerCount = viewerCount;
    }
}
