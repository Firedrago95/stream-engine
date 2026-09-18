package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkChannelResponse(
    Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        String channelId,
        String channelName,
        String channelImageUrl,
        Integer followerCount,
        Boolean verifiedMark,
        Boolean openLive
    ) {
    }
}
