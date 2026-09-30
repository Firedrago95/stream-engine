package io.slice.stream.engine.sampler.domain;

import java.time.Instant;

public record ChatSampleMessage(
        Instant timestamp,
        String channelId,
        String streamerName,
        String content,
        boolean isSubscriber
) {
}
