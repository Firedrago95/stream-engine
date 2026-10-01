package io.slice.stream.engine.sampler.application.dto;

import java.util.List;
import java.util.Map;

public record StartSamplingRequest(
        String tag,
        List<String> channelIds,
        Map<String, String> streamerNames,
        Integer rankFrom,
        Integer rankTo,
        Integer durationMinutes
) {
    public StartSamplingRequest(String tag, List<String> channelIds, Integer rankFrom, Integer rankTo, Integer durationMinutes) {
        this(tag, channelIds, null, rankFrom, rankTo, durationMinutes);
    }
}

