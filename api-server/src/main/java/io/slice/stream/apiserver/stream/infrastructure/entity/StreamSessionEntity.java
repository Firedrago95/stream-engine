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

    @Column(name = "average_viewer_count")
    private Integer averageViewerCount;

    @Column(name = "viewer_sample_count", nullable = false)
    private int viewerSampleCount = 0;

    @Column(name = "session_follower_growth")
    private Integer sessionFollowerGrowth;

    @Column(name = "subscriber_chat_ratio")
    private Double subscriberChatRatio;

    @Column(name = "paid_promotion", nullable = false)
    private boolean paidPromotion;

    @Column(name = "is_adult", nullable = false)
    private boolean isAdult;

    public StreamSessionEntity(String streamId, String sessionId, String title, String categoryName, Instant startedAt, boolean paidPromotion, boolean isAdult) {
        this.streamId = streamId;
        this.sessionId = sessionId;
        this.title = title;
        this.categoryName = categoryName;
        this.startedAt = startedAt;
        this.paidPromotion = paidPromotion;
        this.isAdult = isAdult;
        this.peakViewers = 0;
    }

    public StreamSessionEntity(String streamId, String sessionId, String title, String categoryName, Instant startedAt, boolean paidPromotion) {
        this(streamId, sessionId, title, categoryName, startedAt, paidPromotion, false);
    }

    public StreamSessionEntity(String streamId, String sessionId, String title, String categoryName, Instant startedAt) {
        this(streamId, sessionId, title, categoryName, startedAt, false, false);
    }

    public void markPaidPromotion() {
        this.paidPromotion = true;
    }

    public void updatePaidPromotion(boolean paidPromotion) {
        this.paidPromotion = paidPromotion;
    }

    public void markAdult() {
        this.isAdult = true;
    }

    public void updateAdult(boolean isAdult) {
        this.isAdult = isAdult;
    }

    public void updateMetadata(String title, String categoryName) {
        this.title = title;
        this.categoryName = categoryName;
    }

    public void updatePeakViewers(int viewers) {
        if (this.peakViewers == null || viewers > this.peakViewers) {
            this.peakViewers = viewers;
        }
    }

    public void recordViewerSample(int currentViewers) {
        updatePeakViewers(currentViewers);
        int currentAvg = this.averageViewerCount != null ? this.averageViewerCount : 0;
        int prevCount = Math.max(0, this.viewerSampleCount);
        this.viewerSampleCount = prevCount + 1;
        this.averageViewerCount = (int) Math.round(((long) currentAvg * prevCount + currentViewers) / (double) this.viewerSampleCount);
    }

    public void finishSession(Instant endedAt, Integer finalPeakViewers) {
        this.endedAt = endedAt;
        if (finalPeakViewers != null && (this.peakViewers == null || finalPeakViewers > this.peakViewers)) {
            this.peakViewers = finalPeakViewers;
        }
    }

    public void finishSession(Instant endedAt, Integer finalPeakViewers, Integer averageViewerCount) {
        finishSession(endedAt, finalPeakViewers);
        if (averageViewerCount != null) {
            this.averageViewerCount = averageViewerCount;
        }
    }

    public void finishSession(Instant endedAt, Integer finalPeakViewers, Double averageViewerCount) {
        finishSession(endedAt, finalPeakViewers, averageViewerCount != null ? (int) Math.round(averageViewerCount) : null);
    }

    public void reopen() {
        this.endedAt = null;
        this.subscriberChatRatio = null;
    }

    public void updateAverageViewerCount(Integer averageViewerCount) {
        this.averageViewerCount = averageViewerCount;
    }

    public void updateSessionFollowerGrowth(Integer sessionFollowerGrowth) {
        this.sessionFollowerGrowth = sessionFollowerGrowth;
    }

    public void updateSubscriberChatRatio(Double subscriberChatRatio) {
        this.subscriberChatRatio = subscriberChatRatio;
    }
}
