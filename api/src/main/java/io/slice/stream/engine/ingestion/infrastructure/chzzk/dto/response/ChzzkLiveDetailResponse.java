package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkLiveDetailResponse(
    Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        String status,
        String chatChannelId
    ) {
    }
}
