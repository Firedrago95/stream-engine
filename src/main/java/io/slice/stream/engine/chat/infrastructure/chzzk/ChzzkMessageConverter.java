package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.CmdType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        if (response.body() == null || !response.body().isArray()) {
            return Collections.emptyList(); // 데이터 없음 (정상)
        }

        return StreamSupport.stream(response.body().spliterator(), false)
            .map(bodyNode -> parseSingleMessage(bodyNode, cmdType))
            .toList();
    }

    private ChatMessage parseSingleMessage(JsonNode bodyNode, CmdType cmdType) {
        try {
            ChzzkResponseMessage.Profile profile = jsonMapper.readValue(
                bodyNode.path("profile").asString(),
                ChzzkResponseMessage.Profile.class
            );

            MessageType messageType = (cmdType == CmdType.DONATION) ? MessageType.DONATION : MessageType.TEXT;

            Author author = new Author(
                profile.userIdHash(),
                profile.nickname(),
                profile.profileImageUrl()
            );

            return new ChatMessage(
                messageType,
                author,
                bodyNode.path("msg").asString(),
                LocalDateTime.ofEpochSecond(bodyNode.path("msgTime").asLong() / 1000, 0, ZoneOffset.UTC),
                Map.of()
            );
        } catch (Exception e) {
            throw new RuntimeException("단일 메시지 파싱 실패", e);
        }
    }
}
