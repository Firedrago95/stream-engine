package io.slice.stream.engine.analyzer.domain.similarity;

import java.util.List;
import java.util.Set;

public interface StreamerChatterRepository {

    void saveChatters(String streamId, Set<Long> userHashes, String yearWeek);

    Set<Long> findChatters(String streamId, String yearWeek);

    Set<Long> findChatters(String streamId, List<String> yearWeeks);
}
