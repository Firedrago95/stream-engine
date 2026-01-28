package io.slice.stream.engine.analysis.application;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAnalysisKafkaConsumer {

    private final ChatAnalysisService chatAnalysisService;

    @KafkaListener(
        topics = "chat-messages",
        groupId = "chat-analysis-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ChatMessage chatMessage) {
        log.debug("kafka 메시지 수신: streamId={}, message={}", chatMessage.streamId(), chatMessage.message());
        chatAnalysisService.analyze(chatMessage);
    }
}