package io.slice.stream.apiserver.stream.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "streams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false, unique = true)
    private String streamId;

    @Column(name = "streamer_name", nullable = false)
    private String streamerName;

    private String liveTitle;

    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    private int concurrentUserCount;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(nullable = false)
    private boolean isLive;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "subscriber_count")
    private Integer subscriberCount;

    @Column(nullable = false)
    private Instant lastUpdateAt;

    // 최초 생성
    public StreamEntity(String streamId, String streamerName) {
        this.streamId = streamId;
        this.streamerName = streamerName;
        this.isLive = true;
        this.lastUpdateAt = Instant.now();
    }

    public void heartbeat(String streamerName, String liveTitle, String profileImageUrl, String categoryName, int concurrentUserCount) {
        this.streamerName = streamerName;
        this.liveTitle = liveTitle;
        this.profileImageUrl = profileImageUrl;
        this.categoryName = categoryName;
        this.concurrentUserCount = concurrentUserCount;
        this.isLive = true;
        this.lastUpdateAt = Instant.now(); // 명시적 갱신
    }

    public void markOffline() {
        this.isLive = false;
        this.lastUpdateAt = Instant.now();
    }

    public void updateChannelMetrics(Integer followerCount, Integer subscriberCount) {
        this.followerCount = followerCount;
        this.subscriberCount = subscriberCount;
    }
}
