package io.slice.stream.engine.chat.domain;

import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.CmdType;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class ChatCollector implements ChatMessageListener {

    private final ChatClient chatClient;
    private final String streamId;
    private final ObjectMapper objectMapper;

    public ChatCollector(ChatClient chatClient, String streamId) {
        this.chatClient = chatClient;
        this.streamId = streamId;
        this.objectMapper = new ObjectMapper();
    }

    public void start() {
        try {
            chatClient.connect(streamId, this);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다..", streamId, e);
        }
    }

    @Override
    public void onRawMessage(String rawMessage) {
        JsonNode rootNode = objectMapper.readTree(rawMessage);
        int cmd = rootNode.path("cmd").asInt();
        CmdType cmdType = CmdType.fromInt(cmd);

        if (cmdType == CmdType.CHAT || cmdType == CmdType.DONATION) {
            ChzzkResponseMessage response = objectMapper.treeToValue(rootNode, ChzzkResponseMessage.class);
            if (response.body().isArray()) {
                for (JsonNode bodyNode : response.body()) {
                    ChzzkResponseMessage.Body bodyDto = objectMapper.treeToValue(bodyNode, ChzzkResponseMessage.Body.class);
                    // TODO: MapDTO to ChatMessage domain object and send to the next stage (e.g., Kafka)
                    log.info("[{}] 채팅 수신 : {}", streamId, bodyDto.message());
                }
            }
        }
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

    public void disconnect() {
        chatClient.disconnect();
    }
}
