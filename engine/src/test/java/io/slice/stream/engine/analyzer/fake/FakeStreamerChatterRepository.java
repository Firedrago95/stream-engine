package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.domain.similarity.StreamerChatterRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FakeStreamerChatterRepository implements StreamerChatterRepository {

    private final Map<String, Set<Long>> storage = new HashMap<>();

    @Override
    public void saveChatters(String streamId, Set<Long> userHashes, String yearWeek) {
        if (userHashes == null || userHashes.isEmpty()) {
            return;
        }
        String key = streamId + ":" + yearWeek;
        storage.computeIfAbsent(key, k -> new HashSet<>()).addAll(userHashes);
    }

    @Override
    public Set<Long> findChatters(String streamId, String yearWeek) {
        String key = streamId + ":" + yearWeek;
        return new HashSet<>(storage.getOrDefault(key, Set.of()));
    }

    @Override
    public Set<Long> findChatters(String streamId, List<String> yearWeeks) {
        Set<Long> result = new HashSet<>();
        for (String yw : yearWeeks) {
            result.addAll(findChatters(streamId, yw));
        }
        return result;
    }

    public void clear() {
        storage.clear();
    }
}
