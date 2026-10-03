package io.slice.stream.engine.sampler.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record ChatSampleHeader(
        String type,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant openDate,
        Long openDateEpoch,
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant samplingStartedAt,
        Long samplingStartedAtEpoch,
        String channelId,
        String streamerName
) {
    public static ChatSampleHeader of(Instant openDate, Instant samplingStartedAt, String channelId, String streamerName) {
        Long openEpoch = openDate != null ? openDate.getEpochSecond() : null;
        Long samplingEpoch = samplingStartedAt != null ? samplingStartedAt.getEpochSecond() : null;
        return new ChatSampleHeader("METADATA", openDate, openEpoch, samplingStartedAt, samplingEpoch, channelId, streamerName);
    }
}
