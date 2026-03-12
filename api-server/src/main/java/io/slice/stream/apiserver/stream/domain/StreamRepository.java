package io.slice.stream.apiserver.stream.domain;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreamRepository {

    List<StreamEntity> findActiveStreams(Instant threshold);

    List<StreamEntity> findByStreamerNameContainingIgnoreCase(String keyword);

    void upsertStream(StreamEntity request, Instant currentTime);

    Optional<StreamEntity> findById(String streamId);
}
