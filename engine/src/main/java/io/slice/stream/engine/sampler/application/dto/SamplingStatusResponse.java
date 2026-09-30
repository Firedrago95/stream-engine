package io.slice.stream.engine.sampler.application.dto;

import java.time.Instant;
import java.util.List;

public record SamplingStatusResponse(
        boolean active,
        int activeSessionCount,
        long totalDroppedMessages,
        List<SessionDetail> sessions
) {
    public record SessionDetail(
            String channelId,
            String streamerName,
            String tag,
            Instant startedAt,
            Instant expiresAt,
            long messageCount,
            long fileSizeBytes
    ) {
    }
}
