package io.slice.stream.engine.chat.infrastructure.kafka;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaWarmup {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostConstruct
    public void warmup() {
        try {
            log.info(" Kafka Producer 미리 깨우기 (Warm-up) 시작...");
            // 실제로 메시지를 보내진 않고, 프로듀서 내부 객체만 살짝 건드려서 초기화를 유도합니다.
            // executeInTransaction이나 metrics() 등을 호출하면 프로듀서가 초기화됩니다.
            kafkaTemplate.execute(producer -> {
                log.info(" Kafka Producer 초기화 완료! (Metrics: {})", producer.metrics().size());
                return null;
            });
        } catch (Exception e) {
            log.warn("⚠ Kafka Warm-up 중 에러 발생 (무시 가능): {}", e.getMessage());
        }
    }
}
