package io.slice.stream.apiserver.streamer.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "streamer_follower_snapshots",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_follower_snapshots_stream_date",
            columnNames = {"stream_id", "snapshot_date"}
        )
    },
    indexes = {
        @Index(
            name = "idx_follower_snapshots_stream_date_desc",
            columnList = "stream_id, snapshot_date DESC"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerFollowerSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    @Column(name = "follower_growth", nullable = false)
    private int followerGrowth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StreamerFollowerSnapshotEntity(
        String streamId,
        LocalDate snapshotDate,
        int followerCount,
        int followerGrowth
    ) {
        this.streamId = streamId;
        this.snapshotDate = snapshotDate;
        this.followerCount = followerCount;
        this.followerGrowth = followerGrowth;
        this.createdAt = Instant.now();
    }

    public void updateMetrics(int followerCount, int followerGrowth) {
        this.followerCount = followerCount;
        this.followerGrowth = followerGrowth;
    }
}
