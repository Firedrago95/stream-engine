package io.slice.stream.engine.sampler.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.drive")
public record GoogleDriveProperties(
        boolean enabled,
        String folderId,
        String credentialsPath,
        String credentialsJson
) {
}
