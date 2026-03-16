package io.slice.stream.apiserver.analysis.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis_signals", indexes = {
    @Index(name = "idx_stream_timestamp", columnList = "stream_id, timestamp DESC")
})
@Getter
@NoArgsConstructor
public class AnalysisSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Long firepower;

    @Column(name = "offset_ms")
    private Long offsetMs;

    public AnalysisSignalEntity(String streamId, String sessionId, String status, Instant timestamp, Long firepower, Long offsetMs) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.status = status;
        this.timestamp = timestamp;
        this.firepower = firepower;
        this.offsetMs = offsetMs;
    }
}
