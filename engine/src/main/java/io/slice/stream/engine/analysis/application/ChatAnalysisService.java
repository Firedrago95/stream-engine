package io.slice.stream.engine.analysis.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.slice.stream.engine.analysis.domain.ChatAnalysisResult;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatAnalysisService {

    private final Cache<String, ChatRoomAnalysis> chatRoomAnalyses;
    private final ChatRoomAnalysisRepository chatRoomAnalysisRepository;
    private final Clock clock;

    public ChatAnalysisService(ChatRoomAnalysisRepository chatRoomAnalysisRepository, Clock clock) {
        this.chatRoomAnalysisRepository = chatRoomAnalysisRepository;
        this.clock = clock;
        this.chatRoomAnalyses = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener((String key, ChatRoomAnalysis value, RemovalCause cause) -> {
                if (cause != RemovalCause.REPLACED) {
                    log.info("캐시 만료로 최종 저장 실행: {}", key);
                    saveToRepository(key, value);
                }
            })
            .build();
    }

    public void analyze(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAnalysis chatRoomAnalysis = chatRoomAnalyses.get(streamId, k -> new ChatRoomAnalysis(streamId));

        chatRoomAnalysis.increaseCount();
    }

    @Scheduled(fixedRate = 10_000)
    public void saveAnalyses() {
            chatRoomAnalyses.asMap().forEach(this::saveToRepository);
    }

    private void saveToRepository(String key, ChatRoomAnalysis analysis) {
        try {
            chatRoomAnalysisRepository.save(analysis, clock.instant());
        } catch(Exception e) {
            log.error("채팅 분석 결과 저장 실패 : {}", key, e);
        }
    }

    public ChatRoomAnalysis getAnalysisFor(String streamId) {
        return chatRoomAnalyses.getIfPresent(streamId);
    }

    public Optional<ChatAnalysisResult> getChatAnalysisResult(String streamId) {
        return chatRoomAnalysisRepository.findByStreamId(streamId);
    }
}
