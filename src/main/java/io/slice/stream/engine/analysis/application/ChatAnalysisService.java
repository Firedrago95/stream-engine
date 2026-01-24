package io.slice.stream.engine.analysis.application;

import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatAnalysisService {

    private final Map<String, ChatRoomAnalysis> chatRoomAnalyses = new ConcurrentHashMap<>();
    private final ChatRoomAnalysisRepository chatRoomAnalysisRepository;

    public void analyze(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAnalysis chatRoomAnalysis = chatRoomAnalyses.computeIfAbsent(streamId, k -> new ChatRoomAnalysis(streamId));

        chatRoomAnalysis.increaseCount();
    }

    @Scheduled(fixedRate = 10_000)
    public void saveAnalyses() {
        chatRoomAnalyses.forEach((streamId, analysis) -> {
            chatRoomAnalysisRepository.save(analysis, Instant.now());
        });
    }

    public ChatRoomAnalysis getAnalysisFor(String streamId) {
        return chatRoomAnalyses.get(streamId);
    }
}
