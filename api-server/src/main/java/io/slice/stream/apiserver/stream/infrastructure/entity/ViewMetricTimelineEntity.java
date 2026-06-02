package io.slice.stream.apiserver.stream.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ViewMetricTimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String streamId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private int viewerCount;

    public ViewMetricTimelineEntity(Long id, String streamId, String sessionId, Instant timestamp, int viewerCount) {
        this.id = id;
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.viewerCount = viewerCount;
    }
}
