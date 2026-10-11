package io.slice.stream.apiserver.streamer.domain.repository;

public interface StreamerFollowerGrowthProjection {
    String getStreamId();
    String getStreamerName();
    String getLiveTitle();
    String getProfileImageUrl();
    String getCategoryName();
    boolean getIsLive();
    int getConcurrentUserCount();
    int getFollowerCount();
    int getWeeklyGrowth();
}
