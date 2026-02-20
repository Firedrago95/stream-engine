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

        ChatRoomAggregation chatRoomAggregation = chatRoomAggregations.get(streamId, k -> new ChatRoomAggregation(streamId, Instant.EPOCH));

        chatRoomAggregation.increaseCount(chatMessage.time());
    }

    @Scheduled(fixedRate = 3_000)
    public void saveAggregations() {
        chatRoomAggregations.asMap().forEach(this::saveToRepository);
    }

    private void saveToRepository(String key, ChatRoomAggregation aggregation) {
        try {
            chatRoomAggregationRepository.save(aggregation, aggregation.getLastChatTime());
        } catch (Exception e) {
            log.error("채팅 집계 결과 저장 실패 : {}", key, e);
        }
    }

    public ChatRoomAggregation getAggregationFor(String streamId) {
        return chatRoomAggregations.getIfPresent(streamId);
    }
}
