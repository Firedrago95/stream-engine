package io.slice.stream.engine.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import io.slice.stream.engine.chat.domain.model.Author;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class ChatAnalysisServiceTest {

    @Mock
    ChatRoomAnalysisRepository chatRoomAnalysisRepository;

    ChatAnalysisService chatAnalysisService;

    @BeforeEach
    void setUp() {
        chatAnalysisService = new ChatAnalysisService(chatRoomAnalysisRepository);
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
            () -> assertThat(analysis.getCount()).isEqualTo(1L)
        );
    }

    @Test
    void 정해진_시간마다_해당_채팅방의_채팅수를_저장한다() {
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
        chatAnalysisService.analyze(chatMessage);

        // when
        chatAnalysisService.saveAnalyses();

        // then
        ArgumentCaptor<ChatRoomAnalysis> argumentCaptor = ArgumentCaptor.forClass(ChatRoomAnalysis.class);
        verify(chatRoomAnalysisRepository).save(argumentCaptor.capture(), any(Instant.class));

        ChatRoomAnalysis capturedAnalysis = argumentCaptor.getValue();
        assertAll(
            () -> assertThat(capturedAnalysis.getStreamId()).isEqualTo(streamId),
            () -> assertThat(capturedAnalysis.getCount()).isEqualTo(1L)
        );
    }
}
