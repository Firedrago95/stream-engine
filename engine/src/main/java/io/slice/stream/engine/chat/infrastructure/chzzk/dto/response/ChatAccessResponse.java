package io.slice.stream.engine.chat.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatAccessResponse(
        Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content (
            String accessToken
    ) {}
}
