package io.slice.stream.apiserver.analysis.infrastructure.entity;

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
@Table(name = "stream_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "title")
    private String title;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "peak_viewers")
    private Integer peakViewers;

    public StreamSessionEntity(String streamId, String sessionId, String title, String categoryName, Instant startedAt) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.title = title;
        this.categoryName = categoryName;
        this.startedAt = startedAt;
        this.peakViewers = 0;
    }

    public void finishSession(Instant endedAt, Integer finalPeakViewers) {
        this.endedAt = endedAt;
        if (finalPeakViewers != null && finalPeakViewers > this.peakViewers) {
            this.peakViewers = finalPeakViewers;
        }
    }
}
