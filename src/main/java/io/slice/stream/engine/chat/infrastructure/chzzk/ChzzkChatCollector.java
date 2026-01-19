package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChzzkResponseMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.CmdType;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkChatCollector implements ChatCollector, ChatMessageListener {

    private final ChatClient chatClient;
    private final String streamId;
    private final JsonMapper jsonMapper;

    public ChzzkChatCollector(ChatClient chatClient, String streamId, JsonMapper jsonMapper) {
        this.chatClient = chatClient;
        this.streamId = streamId;
        this.jsonMapper = jsonMapper;
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
                        ChzzkResponseMessage.Body bodyDto = jsonMapper.treeToValue(bodyNode, ChzzkResponseMessage.Body.class);
                        log.info("[{}] 채팅 수신 : {}", streamId, bodyDto.message());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[{}] 메시지 처리 중 오류가 발생했습니다.", streamId, e);
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

    @Override
    public void disconnect() {
        chatClient.disconnect();
    }
}
