package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregationRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Clock;
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
    private final Clock clock;

    public ChatAggregationService(ChatRoomAggregationRepository chatRoomAggregationRepository, Clock clock) {
        this.chatRoomAggregationRepository = chatRoomAggregationRepository;
        this.clock = clock;
        this.chatRoomAggregations = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, ChatRoomAggregation value, RemovalCause cause) -> {
                if (cause != RemovalCause.REPLACED) {
                    log.info("캐시 만료로 최종 저장 실행: {}", key);
                    saveToRepository(key, value);
                }
            })
            .build();
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
        log.info("[Scheduler] 저장 스케줄러 작동 중... 현재 캐시된 스트림 수: {}", chatRoomAggregations.asMap().size());
        chatRoomAggregations.asMap().forEach(this::saveToRepository);
    }

    private void saveToRepository(String key, ChatRoomAggregation aggregation) {
        try {
            log.info("[Redis-Save] 저장 시도 - Key: {}, 누적카운트: {}, 마지막채팅: {}",
                key, aggregation.getCount(), aggregation.getLastChatTime());

            chatRoomAggregationRepository.save(aggregation, aggregation.getLastChatTime());

            log.info("[Redis-Save] 저장 성공 - Key: {}", key);
        } catch (Exception e) {
            log.error("[Redis-Save] 저장 실패 - Key: {}", key, e);
        }
    }

    public ChatRoomAggregation getAggregationFor(String streamId) {
        return chatRoomAggregations.getIfPresent(streamId);
    }
}
