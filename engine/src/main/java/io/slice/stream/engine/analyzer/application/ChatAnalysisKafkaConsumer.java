package io.slice.stream.engine.analyzer.application;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAnalysisKafkaConsumer {

    private final ChatAggregationService chatAggregationService;

    @KafkaListener(
        topics = "chat-messages",
        groupId = "chat-analysis-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ChatMessage chatMessage, Acknowledgment ack) {
        try {
            log.info("[Kafka-Input] 메시지 도달 - Stream: {}, Msg: {}", chatMessage.streamId(), chatMessage.message());
            chatAggregationService.aggregate(chatMessage);
        } catch (Exception e) {
            log.error("[Kafka-Error] 컨슈밍 실패: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }
}
