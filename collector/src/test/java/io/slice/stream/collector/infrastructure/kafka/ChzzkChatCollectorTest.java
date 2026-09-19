package io.slice.stream.collector.infrastructure.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.MessageType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class ChzzkChatCollectorTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate = mock(KafkaTemplate.class);
    private ChzzkChatCollector collector;

    @BeforeEach
    void setUp() {
        collector = new ChzzkChatCollector("stream_test_1", kafkaTemplate, "chat-messages");
    }

    @Test
    @DisplayName("수신된 채팅 메시지 목록을 Kafka 토픽으로 정확히 전송한다")
    void sendMessagesToKafkaTopic() {
        Author author = new Author("user1", "닉네임1", "profile.jpg", false);
        ChatMessage msg1 = new ChatMessage(MessageType.TEXT, author, "안녕하세요", Instant.now(), "stream_test_1", System.currentTimeMillis(), Map.of());
        ChatMessage msg2 = new ChatMessage(MessageType.TEXT, author, "반갑습니다", Instant.now(), "stream_test_1", System.currentTimeMillis(), Map.of());

        collector.onMessages(List.of(msg1, msg2));

        verify(kafkaTemplate, times(1)).send("chat-messages", "stream_test_1", msg1);
        verify(kafkaTemplate, times(1)).send("chat-messages", "stream_test_1", msg2);
    }
}
