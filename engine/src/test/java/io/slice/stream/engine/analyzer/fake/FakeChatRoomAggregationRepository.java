package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatSummary;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeChatRoomAggregationRepository implements ChatRoomAggregationRepository {

    private final Map<String, List<Long>> deltasMap = new HashMap<>();

    public void setFirepowerDeltas(String streamId, List<Long> deltas) {
        deltasMap.put(streamId, deltas != null ? List.copyOf(deltas) : List.of());
    }

    @Override
    public List<Long> getFirepowerDeltas(String streamId, Instant from, Instant to) {
        return deltasMap.getOrDefault(streamId, List.of());
    }

    @Override
    public void save(ChatRoomAggregation chatRoomAggregation, Instant now) {}

    @Override
    public void save(String streamId, long totalCount, Instant now) {}

    @Override
    public Optional<ChatAggregationResult> findByStreamId(String streamId) {
        return Optional.empty();
    }

    @Override
    public ChatSummary incrementSummary(String streamId, long deltaTotal, long deltaSubscriber) {
        return null;
    }

    @Override
    public Optional<ChatSummary> findSummaryByStreamId(String streamId) {
        return Optional.empty();
    }

    @Override
    public void deleteSummary(String streamId) {}
}
