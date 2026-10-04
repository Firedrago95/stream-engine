package io.slice.stream.engine.analyzer.domain.detection;

import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatFirepowerDetector implements HighlightDetector {

    @Override
    public DetectionResult detect(String streamId, List<Long> deltas, StreamTierInfo tierInfo) {
        if (isDataInsufficient(deltas)) {
            return DetectionResult.waiting();
        }

        long currentDelta = deltas.getLast();
        long floor = tierInfo.noiseFloor();

        if (currentDelta >= floor) {
            logPeakDetection(streamId, currentDelta, floor);
            return new DetectionResult(ChatFirepowerStatus.PEAK, currentDelta);
        }

        return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
    }

    private boolean isDataInsufficient(List<Long> deltas) {
        return deltas == null || deltas.isEmpty();
    }

    private void logPeakDetection(String streamId, long currentDelta, long floor) {
        log.info("[PEAK 감지 - 동적 바닥값] 스트림: {}, 화력: {} (바닥값: {})", streamId, currentDelta, floor);
    }
}
