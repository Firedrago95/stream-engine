package io.slice.stream.engine.chat.infrastructure.chzzk.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChzzkAuthRequest(
    @JsonProperty("ver") String version,
    @JsonProperty("cmd") int command,
    @JsonProperty("svcid") String serviceId,
    @JsonProperty("cid") String channelId,
    @JsonProperty("tid") int type,
    @JsonProperty("bdy") AuthRequestBody body
) {
    public record AuthRequestBody(
        @JsonProperty("uid") String userId,
        @JsonProperty("devType") int deviceType,
        @JsonProperty("accTkn") String accessToken,
        @JsonProperty("auth") String auth
    ) {
    }
}
