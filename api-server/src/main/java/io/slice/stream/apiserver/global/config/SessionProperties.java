package io.slice.stream.apiserver.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "session")
public record SessionProperties(
    Duration reLiveGap,
    Duration reLiveOverlapTolerance,
    Duration noiseThreshold,
    int recentFetchLimit
) {
    public SessionProperties {
        if (reLiveGap == null) {
            reLiveGap = Duration.ofMinutes(6);
        }
        if (reLiveOverlapTolerance == null) {
            reLiveOverlapTolerance = Duration.ofMinutes(6);
        }
        if (noiseThreshold == null) {
            noiseThreshold = Duration.ofMinutes(5);
        }
        if (recentFetchLimit <= 0) {
            recentFetchLimit = 50;
        }
    }
}
