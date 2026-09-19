package io.slice.stream.collector.infrastructure.kafka;

import io.slice.stream.core.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaWarmup {

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    @PostConstruct
    public void warmup() {
        try {
            log.info("[Kafka Warm-up] 카프카 프로듀서 사전 초기화 시작");
            kafkaTemplate.execute(producer -> {
                log.info("[Kafka Warm-up] 카프카 프로듀서 초기화 완료 (지표 수: {})", producer.metrics().size());
                return null;
            });
        } catch (Exception e) {
            log.warn("[Kafka Warm-up] 카프카 사전 초기화 중 경고 발생: {}", e.getMessage());
        }
    }
}
