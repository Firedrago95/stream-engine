package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.slice.stream.engine.analyzer.domain.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregationRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
class ChatAggregationServiceTest {

    @Mock
    private ChatRoomAggregationRepository chatRoomAggregationRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private ChatAggregationService chatAggregationService;

    private ChatMessage createChatMessage(String streamId) {
        return new ChatMessage(null, null, "message", null, streamId, null);
    }

    @Test
    void aggregate_새로운_스트림의_첫_채팅_메시지를_집계하면_count가_1이_된다() {
        // given
        String streamId = "newStream";
        ChatMessage chatMessage = createChatMessage(streamId);

        // when
        chatAggregationService.aggregate(chatMessage);

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1L);
    }

    @Test
    void aggregate_기존_스트림에_채팅_메시지가_추가되면_count가_증가한다() {
        // given
        String streamId = "existingStream";
        ChatMessage chatMessage1 = createChatMessage(streamId);
        ChatMessage chatMessage2 = createChatMessage(streamId);

        // when
        chatAggregationService.aggregate(chatMessage1);
        chatAggregationService.aggregate(chatMessage2);

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(2L);
    }

    @Test
    void getChatAggregationResult_Repository를_호출하여_결과를_반환한다() {
        // given
        String streamId = "testStream";
        ChatAggregationResult expected = new ChatAggregationResult(streamId, List.of());
        when(chatRoomAggregationRepository.findByStreamId(streamId)).thenReturn(Optional.of(expected));

        // when
        Optional<ChatAggregationResult> actual = chatAggregationService.getChatAggregationResult(streamId);

        // then
        assertThat(actual).isPresent().contains(expected);
        verify(chatRoomAggregationRepository, times(1)).findByStreamId(streamId);
    }

    @Test
    void saveAggregations_캐시에_있는_모든_집계_결과를_Repository에_저장한다() {
        // given
        Instant fixedNow = Instant.parse("2026-02-12T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);

        ChatRoomAggregation aggregation1 = new ChatRoomAggregation("stream1");
        aggregation1.increaseCount();
        ChatRoomAggregation aggregation2 = new ChatRoomAggregation("stream2");
        aggregation2.increaseCount();
        aggregation2.increaseCount();

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>) ReflectionTestUtils.getField(
            chatAggregationService, "chatRoomAggregations");
        cache.put("stream1", aggregation1);
        cache.put("stream2", aggregation2);

        // when
        chatAggregationService.saveAggregations();

        // then
        verify(chatRoomAggregationRepository, times(1)).save(eq(aggregation1), eq(fixedNow));
        verify(chatRoomAggregationRepository, times(1)).save(eq(aggregation2), eq(fixedNow));
    }

    @Test
    void 캐시에서_데이터가_만료되면_RemovalListener가_실행되어_저장한다() {
        // given
        Instant fixedNow = Instant.parse("2026-02-12T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);

        String streamId = "expiredStream";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(streamId);
        aggregation.increaseCount();

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>)
            ReflectionTestUtils.getField(chatAggregationService, "chatRoomAggregations");
        cache.put(streamId, aggregation);

        // when
        cache.invalidate(streamId);
        cache.cleanUp();

        // then
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(chatRoomAggregationRepository).save(eq(aggregation), eq(fixedNow));
        });
    }
}
