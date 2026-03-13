package io.slice.stream.apiserver.stream.domain;

public enum StreamStatus {
    LIVE,
    OFFLINE,
    ANALYZING;

    public static StreamStatus determine(boolean isLive, boolean isAnalyzing) {
        if (!isLive) return OFFLINE;
        if (isAnalyzing) return ANALYZING;
        return LIVE;
    }
}
