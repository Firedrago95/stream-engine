package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StreamRepositoryImpl implements StreamRepository {

    private final JpaStreamRepository jpaStreamRepository;

    @Override
    public List<StreamEntity> findAllByStreamIdIn(List<String> streamIds) {
        return jpaStreamRepository.findAllByStreamIdIn(streamIds);
    }

    @Override
    public void saveAll(List<StreamEntity> streams) {
        jpaStreamRepository.saveAll(streams);
    }

    @Override
    public Optional<StreamEntity> findByStreamId(String streamId) {
        return jpaStreamRepository.findByStreamId(streamId);
    }

    @Override
    public List<StreamEntity> findActiveStreams(Instant threshold) {
        return jpaStreamRepository.findActiveStreams(threshold);
    }

    @Override
    public List<StreamEntity> findByStreamerNameContainingIgnoreCase(String keyword) {
        return jpaStreamRepository.findByStreamerNameContainingIgnoreCase(keyword);
    }
}
