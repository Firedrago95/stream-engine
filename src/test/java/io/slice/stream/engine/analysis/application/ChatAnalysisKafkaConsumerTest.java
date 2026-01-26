package io.slice.stream.engine.analysis.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.time.LocalDateTime;
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
    ChatAnalysisService chatAnalysisService;

    @InjectMocks
    ChatAnalysisKafkaConsumer chatAnalysisKafkaConsumer;

    @Test
    void 카프카_메시지를_수신하여_분석단을_호출한다() {
        // given
        ChatMessage chatMessage = new ChatMessage(
            MessageType.TEXT,
            new Author("abcd1", "nickname", null),
            "안녕하세요",
            LocalDateTime.now(),
            "abcde1234",
            Map.of()
        );

        // when
        chatAnalysisKafkaConsumer.consume(chatMessage);

        // then
        verify(chatAnalysisService, times(1)).analyze(any(ChatMessage.class));
    }
}
