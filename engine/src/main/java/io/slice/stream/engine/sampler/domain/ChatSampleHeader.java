package io.slice.stream.engine.sampler.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record ChatSampleHeader(
        String type,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant openDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant samplingStartedAt,
        String channelId,
        String streamerName
) {
    public static ChatSampleHeader of(Instant openDate, Instant samplingStartedAt, String channelId, String streamerName) {
        return new ChatSampleHeader("METADATA", openDate, samplingStartedAt, channelId, streamerName);
    }
}
