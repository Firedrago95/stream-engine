package io.slice.stream.apiserver.streamer.domain.repository;

import java.time.Instant;

public interface StreamerLeaderboardProjection {
    String getStreamId();
    String getStreamerName();
    String getLiveTitle();
    String getProfileImageUrl();
    String getCategoryName();
    boolean getIsLive();
    Instant getLastUpdateAt();
    int getConcurrentUserCount();
    int getAverageViewers();
}
