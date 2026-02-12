package io.slice.stream.apiserver.aggregation.domain;

import java.time.Instant;
import java.util.Optional;

public interface ChatRoomAggregationRepository {

    void save(ChatRoomAggregation chatRoomAggregation,  Instant now);

    Optional<ChatAggregationResult> findByStreamId(String streamId);
}
