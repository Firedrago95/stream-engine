package io.slice.stream.engine.analyzer.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatAnalysisKafkaConsumerTest {

    @Mock
    ChatAggregationService chatAggregationService;

    @InjectMocks
    ChatAnalysisKafkaConsumer chatAnalysisKafkaConsumer;

    @Test
    void 카프카_메시지를_수신하여_분석단을_호출한다() {
        // given
        ChatMessage chatMessage = new ChatMessage(
            MessageType.TEXT,
            new Author("abcd1", "nickname", null),
            "안녕하세요",
            Instant.now(),
            "abcde1234",
            Map.of()
        );

        // when
        chatAnalysisKafkaConsumer.consume(chatMessage);

        // then
        verify(chatAggregationService, times(1)).aggregate(any(ChatMessage.class));
    }
}
