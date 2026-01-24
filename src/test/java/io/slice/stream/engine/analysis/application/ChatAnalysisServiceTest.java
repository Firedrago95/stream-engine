package io.slice.stream.engine.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class ChatAnalysisServiceTest {

    ChatAnalysisService chatAnalysisService;

    @BeforeEach
    void setUp() {
        chatAnalysisService = new ChatAnalysisService();
    }

    @Test
    void 새로운_채팅이_들어오면_해당_채팅방의_카운트가_1_증가한다() {
        // given
        String streamId = "chzzk-12345";
        ChatMessage chatMessage = new ChatMessage(
            MessageType.TEXT,
            new Author("abcd", "user1", null),
            "안녕하세요",
            LocalDateTime.now(),
            streamId,
            null
        );

        // when
        chatAnalysisService.analyze(chatMessage);

        // then
        ChatRoomAnalysis analysis = chatAnalysisService.getAnalysisFor(streamId);
        assertAll(
            () -> assertThat(analysis).isNotNull(),
            () -> assertThat(analysis.getCount()).isEqualTo(2)
        );

    }
}
