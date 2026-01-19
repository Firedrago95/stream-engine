package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.chatMessage.Author;
import io.slice.stream.engine.chat.domain.chatMessage.ChatMessage;
import io.slice.stream.engine.chat.domain.chatMessage.MessageType;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.CmdType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkChatCollector implements ChatCollector, ChatMessageListener {

    private final ChatClient chatClient;
    private final String streamId;
    private final JsonMapper jsonMapper;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    public ChzzkChatCollector(
        ChatClient chatClient,
        String streamId,
        JsonMapper jsonMapper,
        KafkaTemplate<String, ChatMessage> kafkaTemplate
    ) {
        this.chatClient = chatClient;
        this.streamId = streamId;
        this.jsonMapper = jsonMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void start() {
        try {
            chatClient.connect(streamId, this);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다..", streamId, e);
        }
    }

    @Override
    public void onMessage(JsonNode rootNode) {
        try {
            int cmd = rootNode.path("cmd").asInt();
            CmdType cmdType = CmdType.fromInt(cmd);

            if (cmdType == CmdType.CHAT || cmdType == CmdType.DONATION) {
                ChzzkResponseMessage response = jsonMapper.treeToValue(rootNode, ChzzkResponseMessage.class);
                if (response.body().isArray()) {
                    for (JsonNode bodyNode : response.body()) {
                        ChzzkResponseMessage.Profile profile = jsonMapper.readValue(bodyNode.path("profile").asText(),
                            ChzzkResponseMessage.Profile.class);
                        ChatMessage chatMessage = toChatMessage(bodyNode, profile, cmdType);
                        kafkaTemplate.send("chat-messages", streamId, chatMessage);
                        log.info("[{}] 메시지를 Kafka에 전송했습니다: {}", streamId, chatMessage.message());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[{}] 메시지 처리 중 오류가 발생했습니다.", streamId, e);
        }
    }

    private ChatMessage toChatMessage(JsonNode bodyNode, ChzzkResponseMessage.Profile profile, CmdType cmdType) {
        MessageType messageType =
            (cmdType == CmdType.DONATION) ? MessageType.DONATION : MessageType.TEXT;

        Author author = new Author(
            profile.userIdHash(),
            profile.nickname(),
            profile.profileImageUrl()
        );

        return new ChatMessage(
            messageType,
            author,
            bodyNode.path("msg").asText(),
            LocalDateTime.ofEpochSecond(bodyNode.path("msgTime").asLong() / 1000, 0, ZoneOffset.UTC),
            Map.of()
        );
    }

    @Override
    public void onConnected() {
        log.info("[{}] ChatCollector 연결.", streamId);
    }

    @Override
    public void onDisconnected() {
        log.info("[{}] ChatCollector 연결 종료.", streamId);
    }

    @Override
    public void onError(Throwable error) {
        log.error("[{}] ChatCollector 에러.", streamId, error);
    }

    @Override
    public void disconnect() {
        chatClient.disconnect();
    }
}
