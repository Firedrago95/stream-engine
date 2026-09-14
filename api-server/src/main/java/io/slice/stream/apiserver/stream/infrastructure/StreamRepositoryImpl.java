package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.util.Collections;
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
    public List<StreamEntity> findActiveStreamsByStreamIds(List<String> streamIds, Instant threshold) {
        if (streamIds == null || streamIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jpaStreamRepository.findActiveStreamsByStreamIds(streamIds, threshold);
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

    @Override
    public List<StreamEntity> findAllStreamersForLeaderboard() {
        return jpaStreamRepository.findAllStreamersForLeaderboard();
    }

    @Override
    public List<StreamEntity> searchAllStreamersForLeaderboard(String keyword) {
        return jpaStreamRepository.searchAllStreamersForLeaderboard(keyword);
    }

    @Override
    public List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(Instant since, int limit) {
        return jpaStreamRepository.findTopStreamersWith30dAvg(since, limit);
    }

    @Override
    public List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(String keyword, Instant since, int limit) {
        return jpaStreamRepository.searchTopStreamersWith30dAvg(keyword, since, limit);
    }
}
