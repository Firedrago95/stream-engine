package io.slice.stream.engine.chat.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record ChzzkResponseMessage(
    @JsonProperty("cmd") int cmd,
    @JsonProperty("svcid") String serviceId,
    @JsonProperty("cid") String channelId,
    @JsonProperty("tid") int type,
    @JsonProperty("bdy") JsonNode body,
    @JsonProperty("sid") String sessionId
) {
    public record Profile(
        String userIdHash,
        String nickname,
        String profileImageUrl,
        Map<String, String> badge,
        Map<String, String> title,
        boolean streamingProperty
    ) {
    }
}
