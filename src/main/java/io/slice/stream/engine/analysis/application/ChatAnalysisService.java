package io.slice.stream.engine.analysis.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatAnalysisService {

    private final Cache<String, ChatRoomAnalysis> chatRoomAnalyses;
    private final ChatRoomAnalysisRepository chatRoomAnalysisRepository;

    public ChatAnalysisService(ChatRoomAnalysisRepository chatRoomAnalysisRepository) {
        this.chatRoomAnalysisRepository = chatRoomAnalysisRepository;
        this.chatRoomAnalyses = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    }

    public void analyze(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAnalysis chatRoomAnalysis = chatRoomAnalyses.get(streamId, k -> new ChatRoomAnalysis(streamId));

        chatRoomAnalysis.increaseCount();
    }

    @Scheduled(fixedRate = 10_000)
    public void saveAnalyses() {
            chatRoomAnalyses.asMap().forEach((streamId, analysis) -> {
                try {
                    chatRoomAnalysisRepository.save(analysis, Instant.now());
                } catch(Exception e) {
                    log.error("채팅 분석 결과 저장 실패 : {}", streamId, e);
                }
            });
    }

    public ChatRoomAnalysis getAnalysisFor(String streamId) {
        return chatRoomAnalyses.getIfPresent(streamId);
    }
}
