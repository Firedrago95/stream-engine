package io.slice.stream.engine.chat.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkResponseMessage(
    int cmd,
    String svcid,
    String cid,
    String tid,
    JsonNode bdy,
    String sid
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
        String profile,
        String extras,
        String msg,
        int msgTypeCode,
        long msgTime
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
        String userIdHash,
        String nickname,
        String profileImageUrl,
        Map<String, String> badge,
        Map<String, String> title,
        JsonNode streamingProperty
    ) {}
}
