package io.slice.stream.engine.chat.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkChatCollectorTest {

    @Mock
    private KafkaTemplate<String, ChatMessage> kafkaTemplate;

    private ChzzkChatCollector chzzkChatCollector;

    private static final String STREAM_ID = "testStream";
    private static final String TOPIC_NAME = "chat-messages";

    @BeforeEach
    void setUp() {
        chzzkChatCollector = new ChzzkChatCollector(STREAM_ID, kafkaTemplate);
    }

    @Test
    void onMessages는_ChatMessage_리스트를_Kafka_토픽으로_전송해야_한다() {
        // given
        Author author = new Author("hash123", "testUser", "img_url", false);
        ChatMessage message = new ChatMessage(MessageType.TEXT, author, "Hello Kafka", Instant.now(), STREAM_ID, 0L, Map.of());
        List<ChatMessage> messages = List.of(message);

        // when
        chzzkChatCollector.onMessages(messages);

        // then
        verify(kafkaTemplate).send(TOPIC_NAME, STREAM_ID, message);
    }

    @Test
    void onMessages에_빈_리스트가_전달되면_아무_메시지도_전송되지_않아야_한다() {
        // given
        List<ChatMessage> emptyMessages = Collections.emptyList();

        // when
        chzzkChatCollector.onMessages(emptyMessages);

        // then
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void onMessages에_여러_메시지가_전달되면_모든_메시지가_Kafka_토픽으로_전송되어야_한다() {
        // given
        Author author1 = new Author("hash1", "user1", "img_url1", false);
        ChatMessage message1 = new ChatMessage(MessageType.TEXT, author1, "First message", Instant.now(), STREAM_ID, 0L, Map.of());
        Author author2 = new Author("hash2", "user2", "img_url2", false);
        ChatMessage message2 = new ChatMessage(MessageType.TEXT, author2, "Second message", Instant.now(), STREAM_ID, 0L, Map.of());
        List<ChatMessage> multipleMessages = List.of(message1, message2);

        // when
        chzzkChatCollector.onMessages(multipleMessages);

        // then
        verify(kafkaTemplate).send(TOPIC_NAME, STREAM_ID, message1);
        verify(kafkaTemplate).send(TOPIC_NAME, STREAM_ID, message2);
    }
}
