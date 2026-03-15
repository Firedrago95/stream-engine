package io.slice.stream.engine.analyzer.application.config;

import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerDetector;
import io.slice.stream.engine.analyzer.domain.detection.HighlightDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyzerDomainConfig {

    @Bean
    public HighlightDetector highlightDetector() {
        return new ChatFirepowerDetector();
    }
}
