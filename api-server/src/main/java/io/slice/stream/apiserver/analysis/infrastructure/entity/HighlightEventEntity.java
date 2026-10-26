package io.slice.stream.apiserver.analysis.infrastructure.entity;

import jakarta.persistence.*;
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

    @Column(name = "session_id", nullable = false) // 👈 신규 추가
    private String sessionId;

    @Column(nullable = false)
    private String category = "GENERAL";

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "start_time_offset", nullable = false)
    private Long startTimeOffset;

    @Column(name = "last_peak_time", nullable = false)
    private Instant lastPeakTime;

    @Column(name = "last_peak_offset", nullable = false)
    private Long lastPeakOffset;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "end_time_offset")
    private Long endTimeOffset;

    @Column(name = "peak_firepower", nullable = false)
    private Long peakFirepower;

    @Column(nullable = false)
    private String status;

    @Column(name = "rank_in_session")
    private Integer rankInSession;

    @Column(name = "peak_z")
    private Double peakZ;

    @Column(name = "llr_score")
    private Double llrScore;

    @Column(name = "top_tags")
    private String topTags = "{\"words\": [], \"emotes\": []}";

    public HighlightEventEntity(
        String streamId,
        String sessionId,
        Instant startTime,
        Long startTimeOffset,
        Instant lastPeakTime,
        Long lastPeakOffset,
        Long peakFirepower
    ) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.startTime = startTime;
        this.startTimeOffset = startTimeOffset;
        this.lastPeakTime = lastPeakTime;
        this.lastPeakOffset = lastPeakOffset;
        this.peakFirepower = peakFirepower;
        this.status = "ONGOING";
    }

    public HighlightEventEntity(
        String streamId,
        String sessionId,
        String category,
        Instant startTime,
        Long startTimeOffset,
        Instant lastPeakTime,
        Long lastPeakOffset,
        Instant endTime,
        Long endTimeOffset,
        Long peakFirepower,
        String status,
        Integer rankInSession,
        Double peakZ,
        Double llrScore,
        String topTags
    ) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.category = category != null ? category : "GENERAL";
        this.startTime = startTime;
        this.startTimeOffset = startTimeOffset;
        this.lastPeakTime = lastPeakTime;
        this.lastPeakOffset = lastPeakOffset;
        this.endTime = endTime;
        this.endTimeOffset = endTimeOffset;
        this.peakFirepower = peakFirepower;
        this.status = status != null ? status : "FINISHED";
        this.rankInSession = rankInSession;
        this.peakZ = peakZ;
        this.llrScore = llrScore;
        this.topTags = topTags != null ? topTags : "{\"words\": [], \"emotes\": []}";
    }

    public void updatePeakFirepower(Long currentFirepower) {
        if (currentFirepower != null && (this.peakFirepower == null || currentFirepower > this.peakFirepower)) {
            this.peakFirepower = currentFirepower;
        }
    }

    // 시간과 오프셋은 항상 세트로 업데이트되어야 함
    public void updateLastPeak(Instant lastPeakTime, Long lastPeakOffset) {
        if (lastPeakTime != null && lastPeakTime.isAfter(this.lastPeakTime)) {
            this.lastPeakTime = lastPeakTime;
            this.lastPeakOffset = lastPeakOffset;
        }
    }

    public void finish(Instant endTime, Long endTimeOffset) {
        this.endTime = endTime;
        this.endTimeOffset = endTimeOffset;
        this.status = "FINISHED";
    }
}
