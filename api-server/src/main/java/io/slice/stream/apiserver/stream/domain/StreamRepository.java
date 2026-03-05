package io.slice.stream.apiserver.stream.domain;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreamRepository {

    List<StreamEntity> findAllByStreamIdIn(List<String> streamIds);

    void saveAll(List<StreamEntity> streams);

    Optional<StreamEntity> findByStreamId(String streamId);

    List<StreamEntity> findActiveStreams(Instant threshold);

    List<StreamEntity> findByStreamerNameContainingIgnoreCase(String keyword);
}
