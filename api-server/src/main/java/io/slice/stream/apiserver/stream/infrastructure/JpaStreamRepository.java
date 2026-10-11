package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerGrowthProjection;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStreamRepository extends JpaRepository<StreamEntity, Long> {

    List<StreamEntity> findAllByStreamIdIn(List<String> streamIds);

    Optional<StreamEntity> findByStreamId(String streamId);

    long countByIsLiveTrue();

    @Modifying
    @Query("""
        UPDATE StreamEntity s 
        SET s.isLive = false, s.concurrentUserCount = 0 
        WHERE s.isLive = true AND s.lastUpdateAt < :threshold
        """)
    int markAllOfflineBefore(@Param("threshold") Instant threshold);

    @Query("""
           SELECT s FROM StreamEntity s 
           WHERE s.isLive = true 
             AND s.lastUpdateAt > :threshold 
           ORDER BY s.concurrentUserCount DESC
           """)
    List<StreamEntity> findActiveStreams(@Param("threshold") Instant threshold);

    @Modifying
    @Query(value = """
        INSERT INTO streams (stream_id, streamer_name, live_title, profile_image_url, category_name, concurrent_user_count, is_live, last_update_at)
            VALUES (:#{#s.streamId}, :#{#s.streamerName}, :#{#s.liveTitle}, :#{#s.profileImageUrl}, :#{#s.categoryName}, :#{#s.concurrentUserCount}, true, :currentTime)
            ON CONFLICT (stream_id)\s
            DO UPDATE SET\s
                streamer_name = EXCLUDED.streamer_name,
                live_title = EXCLUDED.live_title,
                profile_image_url = EXCLUDED.profile_image_url,
                category_name = EXCLUDED.category_name,
                concurrent_user_count = EXCLUDED.concurrent_user_count,
                is_live = true,
                last_update_at = EXCLUDED.last_update_at
        """, nativeQuery = true)
    void upsertStream(@Param("s") StreamEntity s, @Param("currentTime") Instant currentTime);

    @Query("""
           SELECT s FROM StreamEntity s
           WHERE LOWER(s.streamerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           ORDER BY
            CASE WHEN s.isLive = true AND s.lastUpdateAt > :threshold THEN 1 ELSE 0 END DESC,
            s.concurrentUserCount DESC
           """)
    List<StreamEntity> searchByStreamerName(@Param("keyword") String keyword, @Param("threshold") Instant threshold);

    @Query("""
           SELECT s.streamId 
           FROM StreamEntity s 
           WHERE s.lastUpdateAt >= :since
           ORDER BY s.concurrentUserCount DESC
           """)
    List<String> findTopStreamIdsByConcurrentUserCount(@Param("since") Instant since, Pageable pageable);

    @Query("""
           SELECT s FROM StreamEntity s 
           WHERE s.streamId IN (:streamIds)
             AND s.isLive = true 
             AND s.lastUpdateAt > :threshold 
           ORDER BY s.concurrentUserCount DESC
           """)
    List<StreamEntity> findActiveStreamsByStreamIds(
        @Param("streamIds") List<String> streamIds, 
        @Param("threshold") Instant threshold
    );

    @Query("""
           SELECT s FROM StreamEntity s 
           WHERE s.lastUpdateAt >= :since
           ORDER BY s.concurrentUserCount DESC, s.id DESC
           """)
    List<StreamEntity> findAllStreamersForLeaderboard(@Param("since") Instant since, Pageable pageable);

    @Query("""
           SELECT s FROM StreamEntity s 
           WHERE s.lastUpdateAt >= :since
             AND LOWER(s.streamerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           ORDER BY s.concurrentUserCount DESC, s.id DESC
           """)
    List<StreamEntity> searchAllStreamersForLeaderboard(
        @Param("keyword") String keyword, 
        @Param("since") Instant since,
        Pageable pageable
    );

    @Query(value = """
        SELECT 
            s.stream_id AS streamId,
            s.streamer_name AS streamerName,
            s.live_title AS liveTitle,
            s.profile_image_url AS profileImageUrl,
            s.category_name AS categoryName,
            s.is_live AS isLive,
            s.last_update_at AS lastUpdateAt,
            s.concurrent_user_count AS concurrentUserCount,
            CAST(sub.avg_viewers AS integer) AS averageViewers
        FROM streams s
        INNER JOIN (
            SELECT 
                ss.stream_id, 
                ROUND(
                    SUM(ss.average_viewer_count * GREATEST(1, EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at))))
                    / NULLIF(SUM(GREATEST(1, EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at)))), 0)
                ) AS avg_viewers
            FROM stream_sessions ss
            INNER JOIN (
                SELECT s2.stream_id
                FROM stream_sessions s2
                CROSS JOIN LATERAL generate_series(
                    DATE(s2.started_at AT TIME ZONE 'Asia/Seoul'),
                    DATE(COALESCE(s2.ended_at, NOW()) AT TIME ZONE 'Asia/Seoul'),
                    '1 day'::interval
                ) AS d(broadcast_date)
                WHERE s2.started_at >= :since
                  AND s2.average_viewer_count > 0
                  AND EXTRACT(EPOCH FROM (COALESCE(s2.ended_at, NOW()) - s2.started_at)) >= 300
                GROUP BY s2.stream_id
                HAVING COUNT(DISTINCT d.broadcast_date) >= :minDays
            ) active_days ON ss.stream_id = active_days.stream_id
            WHERE ss.started_at >= :since
              AND ss.average_viewer_count > 0
              AND EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at)) >= 300
            GROUP BY ss.stream_id
        ) sub ON s.stream_id = sub.stream_id
        ORDER BY averageViewers DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(
        @Param("since") Instant since,
        @Param("minDays") int minDays,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT 
            s.stream_id AS streamId,
            s.streamer_name AS streamerName,
            s.live_title AS liveTitle,
            s.profile_image_url AS profileImageUrl,
            s.category_name AS categoryName,
            s.is_live AS isLive,
            s.last_update_at AS lastUpdateAt,
            s.concurrent_user_count AS concurrentUserCount,
            CAST(COALESCE(ROUND(
                SUM(ss.average_viewer_count * GREATEST(1, EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at))))
                / NULLIF(SUM(GREATEST(1, EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at)))), 0)
            ), 0) AS integer) AS averageViewers
        FROM streams s
        LEFT JOIN stream_sessions ss 
            ON s.stream_id = ss.stream_id 
            AND ss.started_at >= :since 
            AND ss.average_viewer_count > 0
            AND EXTRACT(EPOCH FROM (COALESCE(ss.ended_at, NOW()) - ss.started_at)) >= 300
        WHERE LOWER(s.streamer_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        GROUP BY s.stream_id, s.streamer_name, s.live_title, s.profile_image_url, s.category_name, s.is_live, s.last_update_at, s.concurrent_user_count, s.id
        ORDER BY averageViewers DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(
        @Param("keyword") String keyword,
        @Param("since") Instant since,
        @Param("limit") int limit
    );

    @Query("""
        SELECT s FROM StreamEntity s
        WHERE s.followerCount IS NOT NULL AND s.followerCount > 0
        ORDER BY s.followerCount DESC, s.id DESC
        """)
    List<StreamEntity> findTopFollowers(Pageable pageable);

    @Query(value = """
        SELECT 
            s.stream_id AS streamId,
            s.streamer_name AS streamerName,
            s.live_title AS liveTitle,
            s.profile_image_url AS profileImageUrl,
            s.category_name AS categoryName,
            s.is_live AS isLive,
            s.concurrent_user_count AS concurrentUserCount,
            COALESCE(s.follower_count, 0) AS followerCount,
            CAST(COALESCE(SUM(snp.follower_growth), 0) AS integer) AS weeklyGrowth
        FROM streamer_follower_snapshots snp
        INNER JOIN streams s ON s.stream_id = snp.stream_id
        WHERE snp.snapshot_date >= :sinceDate
        GROUP BY s.stream_id, s.streamer_name, s.live_title, s.profile_image_url, s.category_name, s.is_live, s.concurrent_user_count, s.follower_count, s.id
        HAVING COALESCE(SUM(snp.follower_growth), 0) > 0
        ORDER BY weeklyGrowth DESC, followerCount DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StreamerFollowerGrowthProjection> findTopFollowerGrowth(
        @Param("sinceDate") LocalDate sinceDate,
        @Param("limit") int limit
    );
}
