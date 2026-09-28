package io.slice.stream.apiserver.streamer.domain.similarity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface StreamerChatterProvider {

    Map<String, Set<Long>> loadRecentChattersForStreamers(
        List<String> streamIds,
        List<String> yearWeeks
    );
}
