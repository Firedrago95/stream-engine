package io.slice.stream.apiserver.analysis.infrastructure;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnalysisRedisKeyManager {

    private static final String PREFIX = "analysis:signals";
    private static final String BUFFER_KEY = PREFIX + ":buffer";
    private static final String PROCESSING_PREFIX = PREFIX + ":processing:";
    private static final String PROCESSING_PATTERN = PROCESSING_PREFIX + "*";

    public String getBufferKey() {
        return BUFFER_KEY;
    }

    public String generateProcessingKey() {
        return PROCESSING_PREFIX + UUID.randomUUID();
    }

    public String getProcessingKeyPattern() {
        return PROCESSING_PATTERN;
    }

    public boolean isProcessingKey(String key) {
        return key != null && key.startsWith(PROCESSING_PREFIX);
    }
}
