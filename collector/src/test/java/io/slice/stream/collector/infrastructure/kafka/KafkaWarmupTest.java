package io.slice.stream.collector.infrastructure.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.core.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations.ProducerCallback;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaWarmupTest {

    @Test
    @DisplayName("KafkaWarmup 실행 시 kafkaTemplate.execute를 호출하여 사전 초기화를 수행한다")
    void warmupExecutesProducerCallback() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ChatMessage> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaWarmup warmup = new KafkaWarmup(kafkaTemplate);

        warmup.warmup();

        verify(kafkaTemplate, times(1)).execute(any(ProducerCallback.class));
    }

    @Test
    @DisplayName("KafkaWarmup 실행 중 예외가 발생해도 예외를 전파하지 않고 안전하게 처리한다")
    void warmupCatchesExceptionSafely() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ChatMessage> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.execute(any(ProducerCallback.class))).thenThrow(new RuntimeException("연결 실패"));
        KafkaWarmup warmup = new KafkaWarmup(kafkaTemplate);

        assertDoesNotThrow(warmup::warmup);
    }
}
