package io.slice.stream.engine.analyzer.domain.detection;

import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.List;

public interface HighlightDetector {

    DetectionResult detect(String streamId, List<Long> deltas, StreamTierInfo tierInfo);
}
