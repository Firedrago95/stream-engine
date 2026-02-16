package io.slice.stream.engine.analyzer.application;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void consume(ChatMessage chatMessage) {
        chatAggregationService.aggregate(chatMessage);
    }
}
