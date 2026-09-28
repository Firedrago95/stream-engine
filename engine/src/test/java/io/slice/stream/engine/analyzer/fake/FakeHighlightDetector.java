package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.detection.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeHighlightDetector implements HighlightDetector {

    private DetectionResult defaultResult;
    private final Map<String, DetectionResult> streamResults = new HashMap<>();

    public void setDefaultResult(DetectionResult defaultResult) {
        this.defaultResult = defaultResult;
    }

    public void setStreamResult(String streamId, DetectionResult result) {
        streamResults.put(streamId, result);
    }

    @Override
    public DetectionResult detect(String streamId, List<Long> deltas, StreamTierInfo tierInfo) {
        if (streamResults.containsKey(streamId)) {
            return streamResults.get(streamId);
        }
        return defaultResult;
    }
}
