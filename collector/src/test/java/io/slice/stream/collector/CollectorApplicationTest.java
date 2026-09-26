package io.slice.stream.collector;

import io.slice.stream.core.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "collector.polling.interval-ms=60000",
    "spring.kafka.admin.auto-create=false"
})
class CollectorApplicationTest {

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private KafkaTemplate<String, ChatMessage> kafkaTemplate;

    @Test
    @DisplayName("스프링 부트 애플리케이션 컨텍스트가 빈 충돌 없이 정상 로드된다")
    void contextLoads() {
    }
}
