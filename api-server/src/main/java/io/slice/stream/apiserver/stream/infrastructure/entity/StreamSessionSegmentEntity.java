package io.slice.stream.apiserver.stream.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stream_session_segments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamSessionSegmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "title")
    private String title;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_offset_ms", nullable = false)
    private Long startOffsetMs;

    @Column(name = "end_offset_ms")
    private Long endOffsetMs;

    public StreamSessionSegmentEntity(
        String streamId,
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt,
        Long startOffsetMs
    ) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.title = title;
        this.categoryName = categoryName;
        this.startedAt = startedAt;
        this.startOffsetMs = startOffsetMs;
    }

    public void endSegment(Instant endedAt, Long endOffsetMs) {
        this.endedAt = endedAt;
        this.endOffsetMs = endOffsetMs;
    }
}
