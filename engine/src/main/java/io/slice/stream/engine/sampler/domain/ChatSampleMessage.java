package io.slice.stream.engine.sampler.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record ChatSampleMessage(
        Instant timestamp,
        @JsonIgnore
        String channelId,
        String authorId,
        String content,
        boolean isSubscriber
) {
}

