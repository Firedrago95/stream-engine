package io.slice.stream.engine.analyzer.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatAnalysisKafkaConsumerTest {

    @Mock
    ChatAggregationService chatAggregationService;

    @Mock
    Acknowledgment ack;

    @InjectMocks
    ChatAnalysisKafkaConsumer chatAnalysisKafkaConsumer;

    @Test
    void 카프카_메시지를_수신하여_분석단을_호출하고_오프셋을_커밋한다() {
        // given
        ChatMessage chatMessage = createChatMessage("안녕하세요");

        // when
        chatAnalysisKafkaConsumer.consume(chatMessage, ack);

        // then
        verify(chatAggregationService, times(1)).aggregate(any(ChatMessage.class));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void 분석_중_예외가_발생해도_오프셋을_커밋하여_Poison_Pill을_방지한다() {
        // given
        ChatMessage chatMessage = createChatMessage("에러 유발 메시지");
        doThrow(new RuntimeException("분석 엔진 일시 오류"))
            .when(chatAggregationService).aggregate(any(ChatMessage.class));

        // when
        chatAnalysisKafkaConsumer.consume(chatMessage, ack);

        // then
        verify(ack, times(1)).acknowledge();
    }

    private ChatMessage createChatMessage(String content) {
        return new ChatMessage(
            MessageType.TEXT,
            new Author("abcd1", "nickname", null),
            content,
            Instant.now(),
            "abcde1234",
            0L,
            Map.of()
        );
    }


}
