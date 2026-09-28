package io.slice.stream.apiserver.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamer.similarity")
public record StreamerSimilarityProperties(
    int minSampleSize,
    double minSimilarityThreshold,
    int topRankLimit
) {

    public StreamerSimilarityProperties {
        if (minSampleSize <= 0) {
            minSampleSize = 100;
        }
        if (minSimilarityThreshold <= 0.0) {
            minSimilarityThreshold = 3.0;
        }
        if (topRankLimit <= 0) {
            topRankLimit = 3;
        }
    }

    public static StreamerSimilarityProperties defaultProperties() {
        return new StreamerSimilarityProperties(100, 3.0, 3);
    }
}
