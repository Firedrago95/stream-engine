package io.slice.stream.engine.chat.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ChzzkResponseMessage(
    @JsonProperty("cmd") int cmd,
    @JsonProperty("svcid") String serviceId,
    @JsonProperty("cid") String channelId,
    @JsonProperty("tid") int type,
    @JsonProperty("bdy") JsonNode body,
    @JsonProperty("sid") String sessionId
) {
    public record Body(
        @JsonProperty("uid") String userId,
        @JsonProperty("profile") String profileJson,
        @JsonProperty("msg") String message,
        @JsonProperty("msgTime") long messageTime,
        @JsonProperty("msgTypeCode") int messageTypeCode,
        @JsonProperty{value = "extras", access = JsonProperty.Access.WRITE_ONLY}
        JsonNode extras,
        @JsonProperty(value = "message", access = JsonProperty.Access.WRITE_ONLY)
        JsonNode messageJson
    ) {
    }
}
