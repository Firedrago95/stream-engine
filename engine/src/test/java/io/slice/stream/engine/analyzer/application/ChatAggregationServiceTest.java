package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSessionSummary;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatAggregationServiceTest {

    @Mock
    private ChatRoomAggregationRepository chatRoomAggregationRepository;

    private MeterRegistry meterRegistry;

    @Mock
    private io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient apiServerClient;

    private ChatAggregationService chatAggregationService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        chatAggregationService = new ChatAggregationService(chatRoomAggregationRepository, apiServerClient, meterRegistry);
    }

    private ChatMessage createChatMessage(String streamId, Instant time) {
        return createChatMessage(streamId, time, false);
    }

    private ChatMessage createChatMessage(String streamId, Instant time, boolean isSubscriber) {
        return new ChatMessage(null, new io.slice.stream.engine.chat.domain.model.Author("test", "test", "url", isSubscriber), "message", time, streamId, 0L, null);
    }

    @Test
    void aggregate_새로운_스트림의_첫_채팅_메시지를_집계하면_count가_1이_된다() {
        // given
        String streamId = "newStream";
        Instant now = Instant.now();
        ChatMessage chatMessage = createChatMessage(streamId, now);

        // when
        chatAggregationService.aggregate(chatMessage);

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1L);
        assertThat(result.getLastChatTime()).isEqualTo(now);
    }

    @Test
    void aggregate_기존_스트림에_채팅_메시지가_추가되면_count가_증가한다() {
        // given
        String streamId = "existingStream";
        Instant time1 = Instant.now();
        Instant time2 = time1.plusSeconds(1);
        ChatMessage chatMessage1 = createChatMessage(streamId, time1);
        ChatMessage chatMessage2 = createChatMessage(streamId, time2);

        // when
        chatAggregationService.aggregate(chatMessage1);
        chatAggregationService.aggregate(chatMessage2);

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(2L);
        assertThat(result.getLastChatTime()).isEqualTo(time2);
    }

    @Test
    void aggregate_구독자_메시지인_경우_subscriberCount도_증가한다() {
        // given
        String streamId = "subStream";
        ChatMessage normalMessage = createChatMessage(streamId, Instant.now(), false);
        ChatMessage subMessage1 = createChatMessage(streamId, Instant.now(), true);
        ChatMessage subMessage2 = createChatMessage(streamId, Instant.now(), true);

        // when
        chatAggregationService.aggregate(normalMessage);
        chatAggregationService.aggregate(subMessage1);
        chatAggregationService.aggregate(subMessage2);

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result.getCount()).isEqualTo(3L);
        assertThat(result.getSubscriberCount()).isEqualTo(2L);
    }
    
    @Test
    void aggregate_오래된_메시지는_lastChatTime을_변경하지_않는다() {
        // given
        String streamId = "idempotencyStream";
        Instant time1 = Instant.now();
        Instant time2 = time1.plusSeconds(10);
        Instant oldTime = time1.minusSeconds(10); // time2보다 오래된 시간

        ChatMessage message1 = createChatMessage(streamId, time1);
        ChatMessage message2 = createChatMessage(streamId, time2);
        ChatMessage oldMessage = createChatMessage(streamId, oldTime);

        // when
        chatAggregationService.aggregate(message1);
        chatAggregationService.aggregate(message2); // lastChatTime은 time2가 됨
        chatAggregationService.aggregate(oldMessage); // 이 메시지는 count만 올리고 lastChatTime은 변경하지 않아야 함

        // then
        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(3L);
        assertThat(result.getLastChatTime()).isEqualTo(time2);
    }

    @Test
    void saveAggregations_캐시에_있는_모든_집계_결과를_Repository에_저장한다() {
        // given
        Instant time1 = Instant.parse("2026-02-12T10:00:00Z");
        Instant time2 = Instant.parse("2026-02-12T10:00:10Z");

        ChatRoomAggregation aggregation1 = new ChatRoomAggregation("stream1", time1);
        aggregation1.increaseCount(time1, false);
        ChatRoomAggregation aggregation2 = new ChatRoomAggregation("stream2", time2);
        aggregation2.increaseCount(time2, false);
        aggregation2.increaseCount(time2, false);

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>) ReflectionTestUtils.getField(
            chatAggregationService, "chatRoomAggregations");
        cache.put("stream1", aggregation1);
        cache.put("stream2", aggregation2);

        // when
        chatAggregationService.saveAggregations();

        // then
        verify(chatRoomAggregationRepository, times(1)).save(eq(aggregation1), eq(time1));
        verify(chatRoomAggregationRepository, times(1)).save(eq(aggregation2), eq(time2));
    }

    @Test
    void 캐시에서_데이터가_만료되면_RemovalListener가_실행되어_저장한다() {
        // given
        Instant fixedNow = Instant.parse("2026-02-12T10:00:00Z");

        String streamId = "expiredStream";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(streamId, fixedNow);
        aggregation.increaseCount(fixedNow, false);

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>)
            ReflectionTestUtils.getField(chatAggregationService, "chatRoomAggregations");
        cache.put(streamId, aggregation);

        // when
        // Caffeine의 expireAfterAccess는 명시적 EVICTION을 직접 트리거하기 어렵습니다.
        // RemovalListener는 비동기적으로 실행될 수 있으므로, cleanUp() 호출 후 awaitility로 검증합니다.
        cache.invalidate(streamId);
        cache.cleanUp();

        // then
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(chatRoomAggregationRepository).save(eq(aggregation), eq(fixedNow));
        });
    }

    @Test
    void Gauge_지표가_정상적으로_등록되어_활성_스트림_수를_반환한다() {
        // given
        chatAggregationService.aggregate(createChatMessage("stream1", Instant.now()));
        chatAggregationService.aggregate(createChatMessage("stream2", Instant.now()));

        // when
        double streamCount = meterRegistry.get("engine.active.streams").gauge().value();

        // then
        assertThat(streamCount).isEqualTo(2.0);
    }

    @Test
    void handleStreamChangedEvent_방송_종료_시_구독자_비율을_계산하여_API로_전송하고_캐시를_비운다() {
        // given
        String streamId = "closedStream";
        // 10개 중 3개가 구독자 채팅 (비율 30.0%)
        for (int i = 0; i < 7; i++) {
            chatAggregationService.aggregate(createChatMessage(streamId, Instant.now(), false));
        }
        for (int i = 0; i < 3; i++) {
            chatAggregationService.aggregate(createChatMessage(streamId, Instant.now(), true));
        }

        io.slice.stream.engine.core.model.StreamTarget closedTarget = new io.slice.stream.engine.core.model.StreamTarget(streamId, "이름", "chat1", 999L, "제목", 100, "url", "cat", Instant.EPOCH);
        io.slice.stream.engine.core.event.StreamChangedEvent event = new io.slice.stream.engine.core.event.StreamChangedEvent(
            Collections.emptySet(),
            Set.of(closedTarget),
            Instant.now()
        );

        // when
        chatAggregationService.handleStreamChangedEvent(event);

        // then
        verify(apiServerClient, times(1)).sendSessionSummaryAsync(any(StreamSessionSummary.class));
        assertThat(chatAggregationService.getAggregationFor(streamId)).isNull(); // 캐시에서 만료됨
    }
}
