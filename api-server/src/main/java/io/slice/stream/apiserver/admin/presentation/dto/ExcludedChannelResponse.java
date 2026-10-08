package io.slice.stream.apiserver.admin.presentation.dto;

import io.slice.stream.apiserver.stream.targeting.TargetStreamerEntity;
import java.time.Instant;

public record ExcludedChannelResponse(
    String channelId,
    String streamerName,
    String reason,
    Instant expiresAt,
    Instant updatedAt
) {
    public static ExcludedChannelResponse from(TargetStreamerEntity entity) {
        return new ExcludedChannelResponse(
            entity.getChannelId(),
            entity.getStreamerName(),
            entity.getReason(),
            entity.getExpiresAt(),
            entity.getUpdatedAt()
        );
    }
}
