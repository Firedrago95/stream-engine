package io.slice.stream.engine.analysis.domain;

import java.time.Instant;
import java.util.Optional;

public interface ChatRoomAnalysisRepository {

    void save(ChatRoomAnalysis chatRoomAnalysis,  Instant now);

    Optional<ChatAnalysisResult> findByStreamId(String streamId);
}
