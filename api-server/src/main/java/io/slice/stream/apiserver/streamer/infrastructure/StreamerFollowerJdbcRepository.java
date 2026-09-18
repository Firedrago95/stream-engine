package io.slice.stream.apiserver.streamer.infrastructure;

import io.slice.stream.apiserver.streamer.application.dto.StreamFollowerUpdateDto;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StreamerFollowerJdbcRepository {

    private static final String UPSERT_SNAPSHOTS_SQL = """
        INSERT INTO streamer_follower_snapshots (stream_id, snapshot_date, follower_count, follower_growth, created_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (stream_id, snapshot_date)
        DO UPDATE SET
            follower_count = EXCLUDED.follower_count,
            follower_growth = EXCLUDED.follower_growth
        """;

    private static final String UPDATE_STREAM_FOLLOWERS_SQL = """
        UPDATE streams
        SET follower_count = ?, last_follower_updated_at = ?
        WHERE stream_id = ?
        """;

    private final JdbcTemplate jdbcTemplate;

    public void batchUpsertSnapshots(List<StreamerFollowerSnapshotEntity> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            UPSERT_SNAPSHOTS_SQL,
            snapshots,
            snapshots.size(),
            this::setSnapshotPreparedStatement
        );
        log.info("[Follower JDBC] 일일 팔로워 스냅샷 {}건 벌크 업서트 완료", snapshots.size());
    }

    public void batchUpdateStreamFollowers(List<StreamFollowerUpdateDto> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            UPDATE_STREAM_FOLLOWERS_SQL,
            updates,
            updates.size(),
            this::setStreamFollowerPreparedStatement
        );
        log.info("[Follower JDBC] 스트리머 마스터 팔로워 정보 {}건 벌크 갱신 완료", updates.size());
    }

    private void setSnapshotPreparedStatement(PreparedStatement ps, StreamerFollowerSnapshotEntity snapshot) {
        try {
            ps.setString(1, snapshot.getStreamId());
            ps.setDate(2, Date.valueOf(snapshot.getSnapshotDate()));
            ps.setInt(3, snapshot.getFollowerCount());
            ps.setInt(4, snapshot.getFollowerGrowth());
            ps.setTimestamp(5, Timestamp.from(snapshot.getCreatedAt()));
        } catch (Exception e) {
            throw new IllegalStateException("[Follower JDBC] 스냅샷 PreparedStatement 파라미터 바인딩 중 오류 발생", e);
        }
    }

    private void setStreamFollowerPreparedStatement(PreparedStatement ps, StreamFollowerUpdateDto update) {
        try {
            ps.setInt(1, update.followerCount());
            ps.setTimestamp(2, Timestamp.from(update.updatedAt()));
            ps.setString(3, update.streamId());
        } catch (Exception e) {
            throw new IllegalStateException("[Follower JDBC] 스트리머 마스터 PreparedStatement 파라미터 바인딩 중 오류 발생", e);
        }
    }
}
