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
    public List<StreamEntity> findActiveStreams(Instant threshold) {
        return jpaStreamRepository.findActiveStreams(threshold);
    }

    @Override
    public List<StreamEntity> searchByStreamerName(String keyword, Instant threshold) {
        return jpaStreamRepository.searchByStreamerName(keyword, threshold);
    }

    @Override
    public void upsertStream(StreamEntity request, Instant currentTime) {
        jpaStreamRepository.upsertStream(request, currentTime);
    }

    @Override
    public Optional<StreamEntity> findById(String streamId) {
        return jpaStreamRepository.findByStreamId(streamId);
    }
}
