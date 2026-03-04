package io.slice.stream.apiserver.analysis.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "highlight_events")
@Getter
@NoArgsConstructor
public class HighlightEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(nullable = false)
    private String category; // 'GENERAL', 'FUNNY' 등 (확장성용)

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "last_peak_time", nullable = false)
    private Instant lastPeakTime; // 10초 유예 계산을 위한 기준점

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "peak_firepower", nullable = false)
    private Long peakFirepower;

    @Column(nullable = false)
    private String status;

    public HighlightEventEntity(String streamId, Instant startTime, Instant lastPeakTime, Long peakFirepower) {
        this.streamId = streamId;
        this.category = "GENERAL";
        this.startTime = startTime;
        this.lastPeakTime = lastPeakTime;
        this.peakFirepower = peakFirepower;
        this.status = "ONGOING";
    }

    public void updatePeakFirepower(Long currentFirepower) {
        if (currentFirepower == null) return;
        if (this.peakFirepower == null || currentFirepower > this.peakFirepower) {
            this.peakFirepower = currentFirepower;
        }
    }

    public void updateLastPeakTime(Instant lastPeakTime) {
        this.lastPeakTime = lastPeakTime;
    }

    public void finish(Instant endTime) {
        this.endTime = endTime;
        this.status = "FINISHED";
    }
}
