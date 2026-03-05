package io.slice.stream.apiserver.stream.domain;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;

public interface StreamRepository {

    List<StreamEntity> findActiveStreams(Instant threshold);

    void upsertStream(StreamEntity request, Instant currentTime);
}
