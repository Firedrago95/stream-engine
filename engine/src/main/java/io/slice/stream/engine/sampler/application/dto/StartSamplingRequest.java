package io.slice.stream.engine.sampler.application.dto;

import java.util.List;

public record StartSamplingRequest(
        String tag,
        List<String> channelIds,
        Integer rankFrom,
        Integer rankTo,
        Integer durationMinutes
) {
}
