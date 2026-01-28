package io.slice.stream.engine.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import io.slice.stream.engine.analysis.domain.ChatAnalysisResult;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatAnalysisServiceTest {

    @Mock
    private ChatRoomAnalysisRepository chatRoomAnalysisRepository;

    @InjectMocks
    private ChatAnalysisService chatAnalysisService;

    private ChatMessage createChatMessage(String streamId) {
        return new ChatMessage(null, null, "message", null, streamId, null);
    }

    @Test
    void analyze_새로운_스트림의_첫_채팅_메시지를_분석하면_count가_1이_된다() {
        // given
        String streamId = "newStream";
        ChatMessage chatMessage = createChatMessage(streamId);

        // when
        chatAnalysisService.analyze(chatMessage);

        // then
        ChatRoomAnalysis result = chatAnalysisService.getAnalysisFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1L);
    }

    @Test
    void analyze_기존_스트림에_채팅_메시지가_추가되면_count가_증가한다() {
        // given
        String streamId = "existingStream";
        ChatMessage chatMessage1 = createChatMessage(streamId);
        ChatMessage chatMessage2 = createChatMessage(streamId);

        // when
        chatAnalysisService.analyze(chatMessage1);
        chatAnalysisService.analyze(chatMessage2);

        // then
        ChatRoomAnalysis result = chatAnalysisService.getAnalysisFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(2L);
    }

    @Test
    void getChatAnalysisResult_Repository를_호출하여_결과를_반환한다() {
        // given
        String streamId = "testStream";
        ChatAnalysisResult expected = new ChatAnalysisResult(streamId, List.of()); // dataPoints는 비어있는 리스트로 초기화
        when(chatRoomAnalysisRepository.findByStreamId(streamId)).thenReturn(Optional.of(expected));

        // when
        Optional<ChatAnalysisResult> actual = chatAnalysisService.getChatAnalysisResult(streamId);

        // then
        assertThat(actual).isPresent().contains(expected);
        verify(chatRoomAnalysisRepository, times(1)).findByStreamId(streamId);
    }

    @Test
    void saveAnalyses_캐시에_있는_모든_분석_결과를_Repository에_저장한다() {
        // given
        ChatRoomAnalysis analysis1 = new ChatRoomAnalysis("stream1");
        analysis1.increaseCount();
        ChatRoomAnalysis analysis2 = new ChatRoomAnalysis("stream2");
        analysis2.increaseCount();
        analysis2.increaseCount();

        // ReflectionTestUtils를 사용하여 Caffeine 캐시에 직접 데이터 주입
        Cache<String, ChatRoomAnalysis> cache = (Cache<String, ChatRoomAnalysis>) ReflectionTestUtils.getField(chatAnalysisService, "chatRoomAnalyses");
        cache.put("stream1", analysis1);
        cache.put("stream2", analysis2);

        // when
        chatAnalysisService.saveAnalyses();

        // then
        verify(chatRoomAnalysisRepository, times(1)).save(eq(analysis1), any(Instant.class));
        verify(chatRoomAnalysisRepository, times(1)).save(eq(analysis2), any(Instant.class));
    }
}