package io.slice.stream.engine.global.config;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessage> kafkaListenerContainerFactory(
        ConsumerFactory<String, ChatMessage> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, ChatMessage> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // 리스너가 Acknowledgment 객체를 인자로 받을 수 있게 하고
        // 수동 커밋 모드를 활성화합니다.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
