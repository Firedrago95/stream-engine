package io.slice.stream.engine.analysis.application;

import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatAnalysisService {

    private final Map<String, ChatRoomAnalysis> chatRoomAnalysises = new ConcurrentHashMap<>();

    public void analyze(ChatMessage chatMessage) {
        String streamId = chatMessage.streamId();

        ChatRoomAnalysis chatRoomAnalysis = chatRoomAnalysises.computeIfAbsent(streamId, k -> new ChatRoomAnalysis());

        chatRoomAnalysis.increaseCount();
    }

    public ChatRoomAnalysis getAnalysisFor(String streamId) {
        if (chatRoomAnalysises.containsKey(streamId)) {
            return chatRoomAnalysises.get(streamId);
        }
        return null;
    }
}
