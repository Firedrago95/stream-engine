package io.slice.stream.engine.chat.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import io.slice.stream.testcontainer.kafka.KafkaTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkChatCollectorTest implements KafkaTestSupport {

    @Autowired
    private KafkaTemplate<String, ChatMessage> kafkaTemplate;
    
    private Consumer<String, ChatMessage> consumer;
    
    private ChzzkChatCollector chzzkChatCollector;
    
    private static final String STREAM_ID = "testStream";
    private static final String TOPIC_NAME = "chat-messages";

    @BeforeEach
    void setUp() {
        chzzkChatCollector = new ChzzkChatCollector(STREAM_ID, kafkaTemplate);
        
        Map<String, Object> props = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class,
            JacksonJsonDeserializer.TRUSTED_PACKAGES, "*",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        ConsumerFactory<String, ChatMessage> consumerFactory = new DefaultKafkaConsumerFactory<>(props);
        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(TOPIC_NAME));
    }
    
    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void onMessages는_ChatMessage_리스트를_Kafka_토픽으로_전송해야_한다() {
        // given
        Author author = new Author("hash123", "testUser", "img_url");
        ChatMessage message = new ChatMessage(MessageType.TEXT, author, "Hello Kafka", LocalDateTime.now(), Map.of());
        List<ChatMessage> messages = List.of(message);
        
        // when
        chzzkChatCollector.onMessages(messages);
        
        // then
        ConsumerRecords<String, ChatMessage> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        ChatMessage consumedMessage = records.iterator().next().value();
        assertThat(consumedMessage.message()).isEqualTo("Hello Kafka");
        assertThat(consumedMessage.author().nickname()).isEqualTo("testUser");
    }

    @Test
    void onMessages에_빈_리스트가_전달되면_아무_메시지도_전송되지_않아야_한다() {
        // given
        List<ChatMessage> emptyMessages = Collections.emptyList();

        // when
        chzzkChatCollector.onMessages(emptyMessages);

        // then
        ConsumerRecords<String, ChatMessage> records = consumer.poll(Duration.ofSeconds(1)); // 짧은 대기 시간
        assertThat(records.count()).isEqualTo(0);
    }

    @Test
    void onMessages에_여러_메시지가_전달되면_모든_메시지가_Kafka_토픽으로_전송되어야_한다() {
        // given
        Author author1 = new Author("hash1", "user1", "img_url1");
        ChatMessage message1 = new ChatMessage(MessageType.TEXT, author1, "First message", LocalDateTime.now(), Map.of());
        Author author2 = new Author("hash2", "user2", "img_url2");
        ChatMessage message2 = new ChatMessage(MessageType.TEXT, author2, "Second message", LocalDateTime.now(), Map.of());
        List<ChatMessage> multipleMessages = List.of(message1, message2);

        // when
        chzzkChatCollector.onMessages(multipleMessages);

        // then
        ConsumerRecords<String, ChatMessage> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(2);

        List<String> consumedContents = StreamSupport.stream(records.records(TOPIC_NAME).spliterator(), false)
            .map(record -> record.value().message())
            .toList();
        assertThat(consumedContents).containsExactlyInAnyOrder("First message", "Second message");
    }
}
