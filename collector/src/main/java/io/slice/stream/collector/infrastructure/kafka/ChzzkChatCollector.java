package io.slice.stream.collector.infrastructure.kafka;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.core.model.ChatMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class ChzzkChatCollector implements ChatMessageListener {

    private static final String DEFAULT_TOPIC = "chat-messages";

    private final String streamId;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;
    private final String topic;

    public ChzzkChatCollector(String streamId, KafkaTemplate<String, ChatMessage> kafkaTemplate) {
        this(streamId, kafkaTemplate, DEFAULT_TOPIC);
    }

    public ChzzkChatCollector(String streamId, KafkaTemplate<String, ChatMessage> kafkaTemplate, String topic) {
        this.streamId = streamId;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic != null && !topic.isBlank() ? topic : DEFAULT_TOPIC;
    }

    @Override
    public void onMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        log.info("[Kafka 전송] {}건의 채팅을 Kafka로 전송합니다. 채널 ID: {}, 토픽: {}", messages.size(), streamId, topic);
        for (ChatMessage msg : messages) {
            CompletableFuture<?> future = kafkaTemplate.send(topic, streamId, msg);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka 전송 실패] 채널 ID: {}, 토픽: {}, 오류: {}", streamId, topic, ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    @Override
    public void onConnected() {
        log.info("[수집기 연결 성공] 채널 ID: {}", streamId);
    }

    @Override
    public void onDisconnected() {
        log.info("[수집기 연결 종료] 채널 ID: {}", streamId);
    }

    @Override
    public void onError(Throwable error) {
        log.error("[수집기 오류 발생] 채널 ID: {}", streamId, error);
    }
}
