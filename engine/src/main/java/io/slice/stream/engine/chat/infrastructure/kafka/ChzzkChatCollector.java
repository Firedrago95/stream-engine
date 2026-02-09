package io.slice.stream.engine.chat.infrastructure.kafka;

import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public class ChzzkChatCollector implements ChatMessageListener {

    private final String streamId;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    @Override
    public void onMessages(List<ChatMessage> messages) {
        messages.forEach(msg -> {
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
}
