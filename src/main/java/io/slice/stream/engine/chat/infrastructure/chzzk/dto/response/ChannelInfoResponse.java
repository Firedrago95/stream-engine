package io.slice.stream.engine.chat.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelInfoResponse(
        ChzzkContent content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChzzkContent(
            String status,
            String chatChannelId
    ) {}
}
