package io.slice.stream.engine.ingestion.domain.targeting;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "targeting")
public class TargetStreamProperties {

    private String trendRedisKey = "target:streamers:pool";
}
