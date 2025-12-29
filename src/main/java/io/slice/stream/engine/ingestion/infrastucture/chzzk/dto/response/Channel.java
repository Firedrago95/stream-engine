package io.slice.stream.engine.ingestion.infrastucture.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
    String channelId,
    String channelName
) {

}
