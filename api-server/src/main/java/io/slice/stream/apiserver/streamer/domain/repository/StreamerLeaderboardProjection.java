package io.slice.stream.apiserver.streamer.domain.repository;

import java.time.OffsetDateTime;

public interface StreamerLeaderboardProjection {
    String getStreamId();
    String getStreamerName();
    String getLiveTitle();
    String getProfileImageUrl();
    String getCategoryName();
    boolean getIsLive();
    OffsetDateTime getLastUpdateAt();
    int getConcurrentUserCount();
    int getAverageViewers();
}
