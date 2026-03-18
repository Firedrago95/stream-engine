package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatAggregationService {

    private final Cache<String, ChatRoomAggregation> chatRoomAggregations;
    private final ChatRoomAggregationRepository chatRoomAggregationRepository;

    public ChatAggregationService(ChatRoomAggregationRepository chatRoomAggregationRepository, MeterRegistry registry) {
        this.chatRoomAggregationRepository = chatRoomAggregationRepository;
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

        chatRoomAggregation.increaseCount(chatMessage.time());
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
            log.error("[Redis-Save] 저장 실패 - Key: {}", key, e);
        }
    }

    public ChatRoomAggregation getAggregationFor(String streamId) {
        return chatRoomAggregations.getIfPresent(streamId);
    }
}
