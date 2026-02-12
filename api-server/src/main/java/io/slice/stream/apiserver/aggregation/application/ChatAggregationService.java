package io.slice.stream.apiserver.aggregation.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult;
import io.slice.stream.apiserver.aggregation.domain.ChatRoomAggregation;
import io.slice.stream.apiserver.aggregation.domain.ChatRoomAggregationRepository;
import io.slice.stream.apiserver.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatAggregationService {

    private final Cache<String, ChatRoomAggregation> chatRoomAggregations;
    private final ChatRoomAggregationRepository chatRoomAggregationRepository;

    public ChatAggregationService(ChatRoomAggregationRepository chatRoomAggregationRepository) {
        this.chatRoomAggregationRepository = chatRoomAggregationRepository;
        this.chatRoomAggregations = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    }

    public void aggregate(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAggregation chatRoomAggregation = chatRoomAggregations.get(streamId, k -> new ChatRoomAggregation(streamId));

        chatRoomAggregation.increaseCount();
    }

    @Scheduled(fixedRate = 10_000)
    public void saveAggregations() {
            chatRoomAggregations.asMap().forEach((streamId, aggregation) -> {
                try {
                    chatRoomAggregationRepository.save(aggregation, Instant.now());
                } catch(Exception e) {
                    log.error("채팅 집계 결과 저장 실패 : {}", streamId, e);
                }
            });
    }

    public ChatRoomAggregation getAggregationFor(String streamId) {
        return chatRoomAggregations.getIfPresent(streamId);
    }

    public Optional<ChatAggregationResult> getChatAggregationResult(String streamId) {
        return chatRoomAggregationRepository.findByStreamId(streamId);
    }
}
