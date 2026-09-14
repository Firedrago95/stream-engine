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

    public TargetStreamerEntity(String channelId, String streamerName, TargetType targetType, boolean isActive) {
        this.channelId = channelId;
        this.streamerName = streamerName;
        this.targetType = targetType;
        this.isActive = isActive;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
