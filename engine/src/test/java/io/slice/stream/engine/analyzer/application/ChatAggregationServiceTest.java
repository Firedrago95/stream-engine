package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatSummary;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSessionSummary;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private ApiServerClient apiServerClient;

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
        return new ChatMessage(null, new Author("test", "test", "url", isSubscriber), "message", time, streamId, 0L, null);
    }

    @Test
    void aggregate_새로운_스트림의_첫_채팅_메시지를_집계하면_count가_1이_된다() {
        String streamId = "newStream";
        Instant now = Instant.now();
        ChatMessage chatMessage = createChatMessage(streamId, now);

        chatAggregationService.aggregate(chatMessage);

        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1L);
        assertThat(result.getLastChatTime()).isEqualTo(now);
    }

    @Test
    void aggregate_기존_스트림에_채팅_메시지가_추가되면_count가_증가한다() {
        String streamId = "existingStream";
        Instant time1 = Instant.now();
        Instant time2 = time1.plusSeconds(1);
        ChatMessage chatMessage1 = createChatMessage(streamId, time1);
        ChatMessage chatMessage2 = createChatMessage(streamId, time2);

        chatAggregationService.aggregate(chatMessage1);
        chatAggregationService.aggregate(chatMessage2);

        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(2L);
        assertThat(result.getLastChatTime()).isEqualTo(time2);
    }

    @Test
    void aggregate_구독자_메시지인_경우_subscriberCount도_증가한다() {
        String streamId = "subStream";
        ChatMessage normalMessage = createChatMessage(streamId, Instant.now(), false);
        ChatMessage subMessage1 = createChatMessage(streamId, Instant.now(), true);
        ChatMessage subMessage2 = createChatMessage(streamId, Instant.now(), true);

        chatAggregationService.aggregate(normalMessage);
        chatAggregationService.aggregate(subMessage1);
        chatAggregationService.aggregate(subMessage2);

        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result.getCount()).isEqualTo(3L);
        assertThat(result.getSubscriberCount()).isEqualTo(2L);
    }

    @Test
    void aggregate_오래된_메시지는_lastChatTime을_변경하지_않는다() {
        String streamId = "idempotencyStream";
        Instant time1 = Instant.now();
        Instant time2 = time1.plusSeconds(10);
        Instant oldTime = time1.minusSeconds(10);

        ChatMessage message1 = createChatMessage(streamId, time1);
        ChatMessage message2 = createChatMessage(streamId, time2);
        ChatMessage oldMessage = createChatMessage(streamId, oldTime);

        chatAggregationService.aggregate(message1);
        chatAggregationService.aggregate(message2);
        chatAggregationService.aggregate(oldMessage);

        ChatRoomAggregation result = chatAggregationService.getAggregationFor(streamId);
        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(3L);
        assertThat(result.getLastChatTime()).isEqualTo(time2);
    }

    @Test
    void saveAggregations_로컬_델타를_Redis_요약에_누적하고_TimeSeries에_저장한다() {
        Instant time1 = Instant.parse("2026-02-12T10:00:00Z");
        Instant time2 = Instant.parse("2026-02-12T10:00:10Z");

        ChatRoomAggregation aggregation1 = new ChatRoomAggregation("stream1", time1);
        aggregation1.increaseCount(time1, false);
        ChatRoomAggregation aggregation2 = new ChatRoomAggregation("stream2", time2);
        aggregation2.increaseCount(time2, false);
        aggregation2.increaseCount(time2, true);

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>) ReflectionTestUtils.getField(
            chatAggregationService, "chatRoomAggregations");
        cache.put("stream1", aggregation1);
        cache.put("stream2", aggregation2);

        when(chatRoomAggregationRepository.incrementSummary(eq("stream1"), eq(1L), eq(0L)))
            .thenReturn(new ChatSummary(10L, 2L));
        when(chatRoomAggregationRepository.incrementSummary(eq("stream2"), eq(2L), eq(1L)))
            .thenReturn(new ChatSummary(20L, 5L));

        chatAggregationService.saveAggregations();

        verify(chatRoomAggregationRepository, times(1)).incrementSummary(eq("stream1"), eq(1L), eq(0L));
        verify(chatRoomAggregationRepository, times(1)).save(eq("stream1"), eq(10L), eq(time1));
        verify(chatRoomAggregationRepository, times(1)).incrementSummary(eq("stream2"), eq(2L), eq(1L));
        verify(chatRoomAggregationRepository, times(1)).save(eq("stream2"), eq(20L), eq(time2));

        assertThat(aggregation1.getCount()).isEqualTo(0L);
        assertThat(aggregation2.getCount()).isEqualTo(0L);
    }

    @Test
    void saveAggregations_델타가_없는_스트림은_저장을_건너뛴다() {
        Instant time1 = Instant.parse("2026-02-12T10:00:00Z");
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", time1);

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>) ReflectionTestUtils.getField(
            chatAggregationService, "chatRoomAggregations");
        cache.put("stream1", aggregation);

        chatAggregationService.saveAggregations();

        verify(chatRoomAggregationRepository, never()).incrementSummary(anyString(), anyLong(), anyLong());
        verify(chatRoomAggregationRepository, never()).save(anyString(), anyLong(), any());
    }

    @Test
    void 캐시에서_데이터가_만료되면_RemovalListener가_실행되어_잔여_델타를_저장한다() {
        Instant fixedNow = Instant.parse("2026-02-12T10:00:00Z");
        String streamId = "expiredStream";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(streamId, fixedNow);
        aggregation.increaseCount(fixedNow, false);

        when(chatRoomAggregationRepository.incrementSummary(eq(streamId), eq(1L), eq(0L)))
            .thenReturn(new ChatSummary(1L, 0L));

        Cache<String, ChatRoomAggregation> cache = (Cache<String, ChatRoomAggregation>)
            ReflectionTestUtils.getField(chatAggregationService, "chatRoomAggregations");
        cache.put(streamId, aggregation);

        cache.invalidate(streamId);
        cache.cleanUp();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(chatRoomAggregationRepository).incrementSummary(eq(streamId), eq(1L), eq(0L));
            verify(chatRoomAggregationRepository).save(eq(streamId), eq(1L), eq(fixedNow));
        });
    }

    @Test
    void Gauge_지표가_정상적으로_등록되어_활성_스트림_수를_반환한다() {
        chatAggregationService.aggregate(createChatMessage("stream1", Instant.now()));
        chatAggregationService.aggregate(createChatMessage("stream2", Instant.now()));

        double streamCount = meterRegistry.get("engine.active.streams").gauge().value();

        assertThat(streamCount).isEqualTo(2.0);
    }

    @Test
    void handleStreamChangedEvent_방송_종료_시_Redis에서_누적_요약을_조회하여_비율을_전송하고_키를_삭제한다() {
        String streamId = "closedStream";
        for (int i = 0; i < 7; i++) {
            chatAggregationService.aggregate(createChatMessage(streamId, Instant.now(), false));
        }
        for (int i = 0; i < 3; i++) {
            chatAggregationService.aggregate(createChatMessage(streamId, Instant.now(), true));
        }

        when(chatRoomAggregationRepository.incrementSummary(eq(streamId), eq(10L), eq(3L)))
            .thenReturn(new ChatSummary(10L, 3L));
        when(chatRoomAggregationRepository.findSummaryByStreamId(streamId))
            .thenReturn(Optional.of(new ChatSummary(10L, 3L)));

        StreamTarget closedTarget = new StreamTarget(streamId, "이름", "chat1", 999L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamChangedEvent event = new StreamChangedEvent(
            Collections.emptySet(),
            Set.of(closedTarget),
            Instant.now()
        );

        chatAggregationService.handleStreamChangedEvent(event);

        ArgumentCaptor<StreamSessionSummary> captor = ArgumentCaptor.forClass(StreamSessionSummary.class);
        verify(apiServerClient, times(1)).sendSessionSummaryAsync(captor.capture());
        StreamSessionSummary summary = captor.getValue();
        assertThat(summary.streamId()).isEqualTo(streamId);
        assertThat(summary.subscriberChatRatio()).isEqualTo(30.0);

        verify(chatRoomAggregationRepository, times(1)).deleteSummary(streamId);
        assertThat(chatAggregationService.getAggregationFor(streamId)).isNull();
    }

    @Test
    void handleStreamChangedEvent_배포_재기동으로_로컬_캐시가_비어있어도_Redis의_누적치를_조회하여_정산한다() {
        String streamId = "restartedStream";
        when(chatRoomAggregationRepository.findSummaryByStreamId(streamId))
            .thenReturn(Optional.of(new ChatSummary(100L, 25L)));

        StreamTarget closedTarget = new StreamTarget(streamId, "이름", "chat1", 123L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamChangedEvent event = new StreamChangedEvent(
            Collections.emptySet(),
            Set.of(closedTarget),
            Instant.now()
        );

        chatAggregationService.handleStreamChangedEvent(event);

        ArgumentCaptor<StreamSessionSummary> captor = ArgumentCaptor.forClass(StreamSessionSummary.class);
        verify(apiServerClient, times(1)).sendSessionSummaryAsync(captor.capture());
        StreamSessionSummary summary = captor.getValue();
        assertThat(summary.streamId()).isEqualTo(streamId);
        assertThat(summary.subscriberChatRatio()).isEqualTo(25.0);

        verify(chatRoomAggregationRepository, times(1)).deleteSummary(streamId);
    }

    @Test
    void handleStreamChangedEvent_특정_스트림_정산_중_예외가_발생해도_나머지_스트림_정산은_계속_수행된다() {
        String failedStreamId = "failedStream";
        String successStreamId = "successStream";

        when(chatRoomAggregationRepository.findSummaryByStreamId(failedStreamId))
            .thenThrow(new RuntimeException("Redis 연결 오류"));
        when(chatRoomAggregationRepository.findSummaryByStreamId(successStreamId))
            .thenReturn(Optional.of(new ChatSummary(50L, 10L)));

        StreamTarget failedTarget = new StreamTarget(failedStreamId, "이름1", "chat1", 111L, "제목1", 100, "url1", "cat1", Instant.EPOCH);
        StreamTarget successTarget = new StreamTarget(successStreamId, "이름2", "chat2", 222L, "제목2", 100, "url2", "cat2", Instant.EPOCH);

        Set<StreamTarget> closedStreams = new LinkedHashSet<>();
        closedStreams.add(failedTarget);
        closedStreams.add(successTarget);

        StreamChangedEvent event = new StreamChangedEvent(
            Collections.emptySet(),
            closedStreams,
            Instant.now()
        );

        chatAggregationService.handleStreamChangedEvent(event);

        ArgumentCaptor<StreamSessionSummary> captor = ArgumentCaptor.forClass(StreamSessionSummary.class);
        verify(apiServerClient, times(1)).sendSessionSummaryAsync(captor.capture());
        assertThat(captor.getValue().streamId()).isEqualTo(successStreamId);
        verify(chatRoomAggregationRepository, times(1)).deleteSummary(successStreamId);
    }
}
