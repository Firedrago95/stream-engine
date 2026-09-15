package io.slice.stream.apiserver.stream.domain;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreamRepository {

    List<StreamEntity> findActiveStreams(Instant threshold);

    List<StreamEntity> findActiveStreamsByStreamIds(List<String> streamIds, Instant threshold);

    List<StreamEntity> searchByStreamerName(String keyword, Instant currentTime);

    void upsertStream(StreamEntity request, Instant currentTime);

    Optional<StreamEntity> findById(String streamId);

    List<StreamEntity> findAllStreamersForLeaderboard(Instant since);

    List<StreamEntity> searchAllStreamersForLeaderboard(String keyword, Instant since);

    List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(Instant since, int minDays, int limit);

    List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(String keyword, Instant since, int limit);
}
