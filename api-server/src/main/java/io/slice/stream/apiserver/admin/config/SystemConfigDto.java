package io.slice.stream.apiserver.admin.config;

import java.time.Instant;

public record SystemConfigDto(
    String configKey,
    String configValue,
    String description,
    String category,
    Instant updatedAt
) {
    public static SystemConfigDto from(SystemConfigEntity entity) {
        return new SystemConfigDto(
            entity.getConfigKey(),
            entity.getConfigValue(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getUpdatedAt()
        );
    }
}
