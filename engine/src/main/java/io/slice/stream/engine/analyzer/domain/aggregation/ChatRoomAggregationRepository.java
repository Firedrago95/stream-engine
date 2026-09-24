package io.slice.stream.engine.analyzer.domain.aggregation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatRoomAggregationRepository {

    void save(ChatRoomAggregation chatRoomAggregation, Instant now);

    void save(String streamId, long totalCount, Instant now);

    Optional<ChatAggregationResult> findByStreamId(String streamId);

    List<Long> getFirepowerDeltas(String streamId, Instant from, Instant to);

    ChatSummary incrementSummary(String streamId, long deltaTotal, long deltaSubscriber);

    Optional<ChatSummary> findSummaryByStreamId(String streamId);

    void deleteSummary(String streamId);
}
