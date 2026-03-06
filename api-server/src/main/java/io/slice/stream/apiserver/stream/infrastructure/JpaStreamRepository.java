package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
           ORDER BY s.lastUpdateAt DESC
           """)
    List<StreamEntity> findActiveStreams(@Param("threshold") Instant threshold);

    List<StreamEntity> findByStreamerNameContainingIgnoreCase(String keyword);

    @Modifying
    @Query(value = """
        INSERT INTO streams (stream_id, streamer_name, live_title, profile_image_url, category_name, is_live, last_update_at)
        VALUES (:#{#s.streamId}, :#{#s.streamerName}, :#{#s.liveTitle}, :#{#s.profileImageUrl}, :#{#s.categoryName}, true, :currentTime)
        ON CONFLICT (stream_id) 
        DO UPDATE SET 
            streamer_name = EXCLUDED.streamer_name,
            live_title = EXCLUDED.live_title,
            profile_image_url = EXCLUDED.profile_image_url,
            category_name = EXCLUDED.category_name,
            is_live = true,
            last_update_at = EXCLUDED.last_update_at
        """, nativeQuery = true)
    void upsertStream(@Param("s") StreamEntity s, @Param("currentTime") Instant currentTime);
}
