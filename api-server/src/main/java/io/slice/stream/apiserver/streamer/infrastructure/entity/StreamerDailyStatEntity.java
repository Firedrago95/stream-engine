package io.slice.stream.apiserver.streamer.infrastructure.entity;

import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "streamer_daily_stats",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_streamer_daily_stats_channel_date",
            columnNames = {"channel_id", "stat_date"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerDailyStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, length = 64)
    private String channelId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "broadcast_duration_seconds", nullable = false)
    private long broadcastDurationSeconds;

    @Column(name = "average_viewers", nullable = false)
    private int averageViewers;

    @Column(name = "peak_viewers", nullable = false)
    private int peakViewers;

    @Column(name = "hours_watched", nullable = false)
    private double hoursWatched;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "follower_growth")
    private Integer followerGrowth;

    @Column(name = "representative_title")
    private String representativeTitle;

    @Column(name = "dominant_category", length = 100)
    private String dominantCategory;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public StreamerDailyStatEntity(
        Long id,
        String channelId,
        LocalDate statDate,
        long broadcastDurationSeconds,
        int averageViewers,
        int peakViewers,
        double hoursWatched,
        Integer followerCount,
        Integer followerGrowth,
        String representativeTitle,
        String dominantCategory,
        int sessionCount
    ) {
        this.id = id;
        this.channelId = channelId;
        this.statDate = statDate;
        this.broadcastDurationSeconds = broadcastDurationSeconds;
        this.averageViewers = averageViewers;
        this.peakViewers = peakViewers;
        this.hoursWatched = hoursWatched;
        this.followerCount = followerCount;
        this.followerGrowth = followerGrowth;
        this.representativeTitle = representativeTitle;
        this.dominantCategory = dominantCategory;
        this.sessionCount = sessionCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static StreamerDailyStatEntity fromDomain(StreamerDailyStat stat) {
        return new StreamerDailyStatEntity(
            stat.id(),
            stat.channelId(),
            stat.statDate(),
            stat.broadcastDurationSeconds(),
            stat.averageViewers(),
            stat.peakViewers(),
            stat.hoursWatched(),
            stat.followerCount(),
            stat.followerGrowth(),
            stat.representativeTitle(),
            stat.dominantCategory(),
            stat.sessionCount()
        );
    }

    public StreamerDailyStat toDomain() {
        return new StreamerDailyStat(
            id,
            channelId,
            statDate,
            broadcastDurationSeconds,
            averageViewers,
            peakViewers,
            hoursWatched,
            followerCount,
            followerGrowth,
            representativeTitle,
            dominantCategory,
            sessionCount
        );
    }
}
