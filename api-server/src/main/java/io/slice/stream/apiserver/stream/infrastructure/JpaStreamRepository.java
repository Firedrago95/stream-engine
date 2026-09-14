package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
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
           ORDER BY s.concurrentUserCount DESC
           """)
    List<String> findTopStreamIdsByConcurrentUserCount(Pageable pageable);

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
           ORDER BY s.concurrentUserCount DESC, s.id DESC
           """)
    List<StreamEntity> findAllStreamersForLeaderboard();

    @Query("""
           SELECT s FROM StreamEntity s 
           WHERE LOWER(s.streamerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           ORDER BY s.concurrentUserCount DESC, s.id DESC
           """)
    List<StreamEntity> searchAllStreamersForLeaderboard(@Param("keyword") String keyword);

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
            CAST(COALESCE(sub.avg_viewers, s.concurrent_user_count) AS integer) AS averageViewers
        FROM streams s
        LEFT JOIN (
            SELECT ss.stream_id, ROUND(AVG(ss.average_viewer_count)) AS avg_viewers
            FROM stream_sessions ss
            WHERE ss.started_at >= :since
              AND ss.average_viewer_count > 0
            GROUP BY ss.stream_id
        ) sub ON s.stream_id = sub.stream_id
        ORDER BY averageViewers DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StreamerLeaderboardProjection> findTopStreamersWith30dAvg(
        @Param("since") Instant since,
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
            CAST(COALESCE(sub.avg_viewers, s.concurrent_user_count) AS integer) AS averageViewers
        FROM streams s
        LEFT JOIN (
            SELECT ss.stream_id, ROUND(AVG(ss.average_viewer_count)) AS avg_viewers
            FROM stream_sessions ss
            WHERE ss.started_at >= :since
              AND ss.average_viewer_count > 0
            GROUP BY ss.stream_id
        ) sub ON s.stream_id = sub.stream_id
        WHERE LOWER(s.streamer_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY averageViewers DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StreamerLeaderboardProjection> searchTopStreamersWith30dAvg(
        @Param("keyword") String keyword,
        @Param("since") Instant since,
        @Param("limit") int limit
    );
}
