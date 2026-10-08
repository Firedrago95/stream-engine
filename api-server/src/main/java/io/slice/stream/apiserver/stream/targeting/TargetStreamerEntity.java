package io.slice.stream.apiserver.stream.targeting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "target_streamers",
    indexes = {
        @Index(name = "idx_target_streamers_active", columnList = "is_active, target_type")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TargetStreamerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, unique = true, length = 64)
    private String channelId;

    @Column(name = "streamer_name", nullable = false, length = 100)
    private String streamerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_target_type", length = 20)
    private TargetType previousTargetType;

    public TargetStreamerEntity(String channelId, String streamerName, TargetType targetType, boolean isActive) {
        this(channelId, streamerName, targetType, isActive, null, null, null);
    }

    public TargetStreamerEntity(
        String channelId,
        String streamerName,
        TargetType targetType,
        boolean isActive,
        String reason,
        Instant expiresAt
    ) {
        this(channelId, streamerName, targetType, isActive, reason, expiresAt, null);
    }

    public TargetStreamerEntity(
        String channelId,
        String streamerName,
        TargetType targetType,
        boolean isActive,
        String reason,
        Instant expiresAt,
        TargetType previousTargetType
    ) {
        this.channelId = channelId;
        this.streamerName = streamerName;
        this.targetType = targetType;
        this.isActive = isActive;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.previousTargetType = previousTargetType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void exclude(String reason, Instant expiresAt) {
        if (this.targetType != TargetType.EXCLUDED) {
            this.previousTargetType = this.targetType;
        }
        this.targetType = TargetType.EXCLUDED;
        this.isActive = true;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void restore() {
        if (this.previousTargetType != null) {
            this.targetType = this.previousTargetType;
            this.isActive = true;
            this.previousTargetType = null;
        } else {
            this.isActive = false;
        }
        this.reason = null;
        this.expiresAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isEffectiveExcluded(Instant now) {
        return targetType == TargetType.EXCLUDED && isActive && !isExpired(now);
    }
}
