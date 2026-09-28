package io.slice.stream.apiserver.streamer.fake;

import io.slice.stream.apiserver.streamer.domain.similarity.StreamerChatterProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FakeStreamerChatterProvider implements StreamerChatterProvider {

    private final Map<String, Set<Long>> storage = new HashMap<>();

    public void setChatters(String streamId, Set<Long> chatters) {
        storage.put(streamId, chatters);
    }

    @Override
    public Map<String, Set<Long>> loadRecentChattersForStreamers(
        List<String> streamIds,
        List<String> yearWeeks
    ) {
        Map<String, Set<Long>> result = new HashMap<>();
        for (String id : streamIds) {
            result.put(id, storage.getOrDefault(id, Set.of()));
        }
        return result;
    }

    public void clear() {
        storage.clear();
    }
}
