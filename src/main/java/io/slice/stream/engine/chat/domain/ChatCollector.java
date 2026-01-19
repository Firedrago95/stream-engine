package io.slice.stream.engine.chat.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.engine.chat.infrastructure.chzzk.CmdType;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatCollector implements ChatMessageListener {

    private final ChatClient chatClient;
    private final String streamId;
    private final ObjectMapper objectMapper;

    public ChatCollector(ChatClient chatClient, String streamId) {
        this.chatClient = chatClient;
        this.streamId = streamId;
        this.objectMapper = new ObjectMapper(); // Initialize ObjectMapper
    }

    public void start() {
        try {
            chatClient.connect(streamId, this);
        } catch (Exception e) {
            log.error("[{}] Failed to start chat connection.", streamId, e);
        }
    }

    @Override
    public void onRawMessage(String rawMessage) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawMessage);
            int cmd = rootNode.path("cmd").asInt();
            CmdType cmdType = CmdType.fromInt(cmd);

            if (cmdType == CmdType.CHAT || cmdType == CmdType.DONATION) {
                ChzzkResponseMessage response = objectMapper.treeToValue(rootNode, ChzzkResponseMessage.class);
                if (response.body().isArray()) {
                    for (JsonNode bodyNode : response.body()) {
                        ChzzkResponseMessage.Body bodyDto = objectMapper.treeToValue(bodyNode, ChzzkResponseMessage.Body.class);
                        // TODO: Map Body DTO to ChatMessage domain object and send to the next stage (e.g., Kafka)
                        log.info("[{}] Received Chat: {}", streamId, bodyDto.message());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("[{}] Failed to parse chat message: {}", streamId, rawMessage, e);
        }
    }

    @Override
    public void onConnected() {
        log.info("[{}] ChatCollector connected.", streamId);
    }

    @Override
    public void onDisconnected() {
        log.info("[{}] ChatCollector disconnected.", streamId);
    }

    @Override
    public void onError(Throwable error) {
        log.error("[{}] ChatCollector error.", streamId, error);
    }

    public void disconnect() {
        chatClient.disconnect();
    }
}
