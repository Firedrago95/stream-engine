package io.slice.stream.apiserver.admin.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "system_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfigEntity {

    @Id
    @Column(name = "config_key", nullable = false, length = 64)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 255)
    private String configValue;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public SystemConfigEntity(String configKey, String configValue, String description, String category) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void updateValue(String newValue) {
        this.configValue = newValue;
        this.updatedAt = Instant.now();
    }
}
