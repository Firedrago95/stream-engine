package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerGrowthProjection;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StreamRepositoryImpl implements StreamRepository {

    private static final String BATCH_UPSERT_STREAMS_SQL = """
        INSERT INTO streams (stream_id, streamer_name, live_title, profile_image_url, category_name, concurrent_user_count, is_live, last_update_at)
        VALUES (?, ?, ?, ?, ?, ?, true, ?)
        ON CONFLICT (stream_id)
        DO UPDATE SET
            streamer_name = EXCLUDED.streamer_name,
            live_title = EXCLUDED.live_title,
            profile_image_url = EXCLUDED.profile_image_url,
            category_name = EXCLUDED.category_name,
            concurrent_user_count = EXCLUDED.concurrent_user_count,
            is_live = true,
            last_update_at = EXCLUDED.last_update_at
        """;

    private final JpaStreamRepository jpaStreamRepository;
    private final JdbcTemplate jdbcTemplate;

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
    public void batchUpsertStreams(List<StreamEntity> streams, Instant currentTime) {
        if (streams == null || streams.isEmpty()) {
            return;
        }

        Timestamp lastUpdateTimestamp = Timestamp.from(currentTime);
        jdbcTemplate.batchUpdate(
            BATCH_UPSERT_STREAMS_SQL,
            streams,
            streams.size(),
            (PreparedStatement ps, StreamEntity s) -> {
                ps.setString(1, s.getStreamId());
                ps.setString(2, s.getStreamerName());
                ps.setString(3, s.getLiveTitle());
                ps.setString(4, s.getProfileImageUrl());
                ps.setString(5, s.getCategoryName());
                ps.setInt(6, s.getConcurrentUserCount());
                ps.setTimestamp(7, lastUpdateTimestamp);
            }
        );
    }

    @Override
    public Optional<StreamEntity> findById(String streamId) {
        return jpaStreamRepository.findByStreamId(streamId);
    }

    @Override
    public List<StreamEntity> findAllStreamersForLeaderboard(Instant since, Pageable pageable) {
        return jpaStreamRepository.findAllStreamersForLeaderboard(since, pageable);
    }

    @Override
    public List<StreamEntity> searchAllStreamersForLeaderboard(String keyword, Instant since, Pageable pageable) {
        return jpaStreamRepository.searchAllStreamersForLeaderboard(keyword, since, pageable);
    }

    @Override
    public List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(
        Instant since,
        int minDays,
        int limit
    ) {
        return jpaStreamRepository.findTopStreamersWith30dAvg(since, minDays, limit);
    }

    @Override
    public List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(String keyword, Instant since, int limit) {
        return jpaStreamRepository.searchTopStreamersWith30dAvg(keyword, since, limit);
    }

    @Override
    public List<StreamEntity> findAllByStreamIdIn(List<String> streamIds) {
        if (streamIds == null || streamIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jpaStreamRepository.findAllByStreamIdIn(streamIds);
    }

    @Override
    public int markAllOfflineBefore(Instant threshold) {
        return jpaStreamRepository.markAllOfflineBefore(threshold);
    }

    @Override
    public List<StreamEntity> findTopFollowers(Pageable pageable) {
        return jpaStreamRepository.findTopFollowers(pageable);
    }

    @Override
    public List<StreamerFollowerGrowthProjection> findTopFollowerGrowth(LocalDate sinceDate, int limit) {
        return jpaStreamRepository.findTopFollowerGrowth(sinceDate, limit);
    }

    @Override
    public List<String> findTopStreamIdsByConcurrentUserCount(Instant since, Pageable pageable) {
        return jpaStreamRepository.findTopStreamIdsByConcurrentUserCount(since, pageable);
    }
}
