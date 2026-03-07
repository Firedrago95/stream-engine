package io.slice.stream.engine.analyzer.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.concurrent.TimeUnit;
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
    private final MeterRegistry meterRegistry;

    @KafkaListener(
        topics = "chat-messages",
        groupId = "chat-analysis-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ChatMessage chatMessage, Acknowledgment ack) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("[Kafka-Input] 메시지 도달 - Stream: {}, Msg: {}", chatMessage.streamId(), chatMessage.message());
            }
            chatAggregationService.aggregate(chatMessage);

            long latency = System.currentTimeMillis() - chatMessage.ingestedAt();
            meterRegistry.timer("analysis.processing.time")
                .record(latency, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("[Kafka-Error] 컨슈밍 실패: {}", e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }
}
