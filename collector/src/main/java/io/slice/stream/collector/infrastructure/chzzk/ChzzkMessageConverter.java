package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.collector.infrastructure.chzzk.websocket.CmdType;
import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.MessageType;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChzzkMessageConverter {

    private final JsonMapper jsonMapper;

    public List<ChatMessage> convert(JsonNode rootNode, String channelId) {
        int cmd = rootNode.path("cmd").asInt();
        CmdType cmdType = CmdType.fromInt(cmd);

        if (cmdType != CmdType.CHAT && cmdType != CmdType.DONATION) {
            return Collections.emptyList();
        }

        ChzzkResponseMessage response = jsonMapper.treeToValue(rootNode, ChzzkResponseMessage.class);
        if (response.bdy() == null || !response.bdy().isArray()) {
            return Collections.emptyList();
        }

        return StreamSupport.stream(response.bdy().spliterator(), false)
            .map(bodyNode -> parseSingleMessage(bodyNode, cmdType, channelId))
            .filter(Objects::nonNull)
            .toList();
    }

    private ChatMessage parseSingleMessage(JsonNode bodyNode, CmdType cmdType, String streamId) {
        try {
            MessageType messageType = (cmdType == CmdType.DONATION) ? MessageType.DONATION : MessageType.TEXT;
            Author author = extractAuthor(bodyNode);

            return new ChatMessage(
                messageType,
                author,
                bodyNode.path("msg").asText(""),
                Instant.ofEpochMilli(bodyNode.path("msgTime").asLong()),
                streamId,
                System.currentTimeMillis(),
                Map.of()
            );
        } catch (Exception e) {
            log.error("단일 채팅 메시지 파싱 실패: {} | 원인: {}", bodyNode.toString().replaceAll("[\r\n]", " "), e.getMessage(), e);
            return null;
        }
    }

    private Author extractAuthor(JsonNode bodyNode) {
        try {
            JsonNode profileNode = bodyNode.path("profile");
            if (profileNode.isMissingNode() || profileNode.isNull() || profileNode.asText().isBlank() || "null".equals(profileNode.asText())) {
                return new Author("anonymous", "익명", null, false);
            }
            ChzzkResponseMessage.Profile profile = jsonMapper.readValue(
                profileNode.asText(),
                ChzzkResponseMessage.Profile.class
            );
            boolean isSubscriber = profile.streamingProperty() != null && profile.streamingProperty().has("subscription");
            return new Author(
                profile.userIdHash(),
                profile.nickname(),
                profile.profileImageUrl(),
                isSubscriber
            );
        } catch (Exception e) {
            return new Author("anonymous", "익명", null, false);
        }
    }
}
