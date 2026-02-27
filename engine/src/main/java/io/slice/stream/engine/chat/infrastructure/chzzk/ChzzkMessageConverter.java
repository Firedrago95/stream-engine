package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.CmdType;
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

    public List<ChatMessage> convert(JsonNode rootNode) {
        int cmd = rootNode.path("cmd").asInt();
        CmdType cmdType = CmdType.fromInt(cmd);

        if (cmdType != CmdType.CHAT && cmdType != CmdType.DONATION) {
            return Collections.emptyList(); // 비즈니스 로직상 무시 (정상)
        }

        ChzzkResponseMessage response;
        response = jsonMapper.treeToValue(rootNode, ChzzkResponseMessage.class);

        if (response.bdy() == null || !response.bdy().isArray()) {
            return Collections.emptyList(); // 데이터 없음 (정상)
        }

        String streamId = response.cid();

        return StreamSupport.stream(response.bdy().spliterator(), false)
            .map(bodyNode -> parseSingleMessage(bodyNode, cmdType, streamId))
            .filter(Objects::nonNull)
            .toList();
    }

    private ChatMessage parseSingleMessage(JsonNode bodyNode, CmdType cmdType, String streamId) {
        try {
            MessageType messageType = (cmdType == CmdType.DONATION) ? MessageType.DONATION : MessageType.TEXT;
            Author author;

            JsonNode profileNode = bodyNode.path("profile");
            if (profileNode.isMissingNode() || profileNode.isNull() || profileNode.asText().isBlank() || profileNode.asText().equals("null")) {
                author = new Author("anonymous", "익명", null);
            } else {
                ChzzkResponseMessage.Profile profile = jsonMapper.readValue(
                    profileNode.asText(),
                    ChzzkResponseMessage.Profile.class
                );
                author = new Author(
                    profile.userIdHash(),
                    profile.nickname(),
                    profile.profileImageUrl()
                );
            }

            ChatMessage chatMessage = new ChatMessage(
                messageType,
                author,
                bodyNode.path("msg").asText(""), // asString() 대신 asText("")로 null 방어
                Instant.ofEpochMilli(bodyNode.path("msgTime").asLong()),
                streamId,
                Map.of()
            );

            log.info("[채팅 파싱 확인] 할당된 streamId: {}, 메시지: {}", streamId, chatMessage.message());

            return chatMessage;
        } catch (Exception e) {
            log.error("단일 채팅 메시지 파싱 실패: {} | 원인: {}", bodyNode.toString().replaceAll("[\r\n]", " "), e.getMessage(), e);
            return null;
        }
    }
}
