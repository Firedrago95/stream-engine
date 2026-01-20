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
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;

@Slf4j
public class ChzzkChatCollector implements ChatCollector, ChatMessageListener {

    private final ChatClient chatClient;
    private final String streamId;
    private final ChzzkMessageConverter messageConverter;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    private volatile boolean isManualDisconnect = false;
    private int retryCount = 0;

    public ChzzkChatCollector(
        ChatClient chatClient,
        String streamId,
        ChzzkMessageConverter messageConverter,
        KafkaTemplate<String, ChatMessage> kafkaTemplate
    ) {
        this.chatClient = chatClient;
        this.streamId = streamId;
        this.messageConverter = messageConverter;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void start() {
        this.isManualDisconnect = false;
        connect();
    }

    @Override
    public void onMessage(JsonNode rootNode) {
        List<ChatMessage> chatMessages = messageConverter.convert(rootNode);

        chatMessages.forEach(msg -> {
            kafkaTemplate.send("chat-messages", streamId, msg);
            log.debug("[{}] kafka 전송 완료: {}", streamId, msg.message());
        });
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

    private void connect() {
        if (isManualDisconnect) return;

        try {
            chatClient.connect(streamId, this);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다..", streamId, e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isManualDisconnect) return;

        long delayMillis = calcualteBackoffDelay();

        Thread.ofVirtual().name("retry-thread-" + streamId).start(() -> {
            try {
                log.info("[{}] {}ms 후 재연결을 시도합니다. (시도 횟수: {})", streamId, delayMillis, retryCount + 1);
                Thread.sleep(delayMillis);

                if (!isManualDisconnect) connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private long calcualteBackoffDelay() {
        long delay = 1000L * (1L << Math.min(retryCount, 5));
        retryCount++;
        return Math.min(delay, 30000L);
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
}
