package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSessionSummary;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatAggregationService {

    private final Cache<String, ChatRoomAggregation> chatRoomAggregations;
    private final ChatRoomAggregationRepository chatRoomAggregationRepository;
    private final ApiServerClient apiServerClient;
    private final Counter redisSaveErrorCounter;

    public ChatAggregationService(
        ChatRoomAggregationRepository chatRoomAggregationRepository,
        ApiServerClient apiServerClient,
        MeterRegistry registry
    ) {
        this.chatRoomAggregationRepository = chatRoomAggregationRepository;
        this.apiServerClient = apiServerClient;
        this.redisSaveErrorCounter = Counter.builder("engine.redis.save.errors")
            .description("Redis TimeSeries 화력 저장 실패 누적 수")
            .register(registry);
        this.chatRoomAggregations = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, ChatRoomAggregation value, RemovalCause cause) -> {
                if (cause != RemovalCause.REPLACED) {
                    saveToRepository(key, value);
                }
            })
            .build();

        Gauge.builder("engine.active.streams", chatRoomAggregations, cache -> cache.asMap().size())
            .description("현재 엔진에서 분석 중인 활성 스트림 수")
            .register(registry);
    }

    public void aggregate(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAggregation chatRoomAggregation = chatRoomAggregations.get(
            streamId,
            // 모든 실제 메시지는 EPOCH 이후이므로 null guard 없이 갱신되도록 보초값 설정
            k -> new ChatRoomAggregation(streamId, Instant.EPOCH)
        );

        chatRoomAggregation.increaseCount(
            chatMessage.time(),
            chatMessage.author().isSubscriber()
        );
    }

    @Scheduled(fixedRate = 3_000)
    public void saveAggregations() {
        if (log.isDebugEnabled()) {
            log.debug("[Scheduler] Redis 저장 작업 수행 중... (대상 스트림: {}개)", chatRoomAggregations.asMap().size());
        }
        chatRoomAggregations.asMap().forEach(this::saveToRepository);
    }

    private void saveToRepository(String key, ChatRoomAggregation aggregation) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("[Redis-Save] 저장 시도 - Key: {}, 누적카운트: {}, 마지막채팅: {}",
                    key, aggregation.getCount(), aggregation.getLastChatTime());
            }
            chatRoomAggregationRepository.save(aggregation, aggregation.getLastChatTime());

        } catch (Exception e) {
            redisSaveErrorCounter.increment();
            log.error("[Redis-Save] 저장 실패 - Key: {}", key, e);
        }
    }

    public ChatRoomAggregation getAggregationFor(String streamId) {
        return chatRoomAggregations.getIfPresent(streamId);
    }

    @EventListener
    public void handleStreamChangedEvent(StreamChangedEvent event) {
        if (event.closedStreams().isEmpty()) return;

        Instant changedAt = event.changedAt();
        for (StreamTarget closedStream : event.closedStreams()) {
            String closedStreamId = closedStream.channelId();
            String closedLiveId = String.valueOf(closedStream.liveId());
            ChatRoomAggregation aggregation = chatRoomAggregations.getIfPresent(closedStreamId);

            if (aggregation != null) {
                Long total = aggregation.getCount();
                Long sub = aggregation.getSubscriberCount();

                double ratio = (total == 0) ? 0.0 : Math.round(((double) sub / total) * 1000.0) / 10.0;

                log.info("[정산 완료] 스트림 {} 종료. 총 채팅: {}, 구독자 채팅: {}, 비율: {}", closedStreamId, total, sub, ratio);

                apiServerClient.sendSessionSummaryAsync(new StreamSessionSummary(closedStreamId, closedLiveId, ratio, changedAt));

                chatRoomAggregations.invalidate(closedStreamId);
            }
        }
    }
}
