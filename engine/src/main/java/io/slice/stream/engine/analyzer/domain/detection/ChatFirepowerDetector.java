package io.slice.stream.engine.analyzer.domain.detection;

import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatFirepowerDetector implements HighlightDetector {

    private static final int MIN_DATA_POINTS_FOR_ANALYSIS = 30;
    private static final int WINDOW_TICKS = 60;
    private static final int MASKING_EXCLUSION_TICKS = 5;
    private static final double NORMAL_CONSISTENCY_FACTOR = 1.4826;
    private static final double MIN_MAD_FLOOR = 0.5;

    @Override
    public DetectionResult detect(String streamId, List<Long> deltas, StreamTierInfo tierInfo) {
        if (isDataInsufficient(deltas)) {
            return DetectionResult.waiting();
        }

        long currentDelta = deltas.getLast();

        if (currentDelta < tierInfo.noiseFloor() || currentDelta < tierInfo.minFirepowerCutoff()) {
            return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
        }

        List<Long> history = extractHistory(deltas);
        if (history.isEmpty()) {
            return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
        }

        return evaluateRobustFirepower(streamId, currentDelta, history, tierInfo);
    }

    private boolean isDataInsufficient(List<Long> deltas) {
        return deltas == null || deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS;
    }

    private List<Long> extractHistory(List<Long> deltas) {
        int endIndex = deltas.size() - MASKING_EXCLUSION_TICKS;
        int startIndex = Math.max(0, endIndex - WINDOW_TICKS);
        if (startIndex >= endIndex) {
            return Collections.emptyList();
        }
        return new ArrayList<>(deltas.subList(startIndex, endIndex));
    }

    private DetectionResult evaluateRobustFirepower(String streamId, long currentDelta, List<Long> history, StreamTierInfo tierInfo) {
        double median = calculateMedian(history);
        double mad = calculateMad(history, median);
        double robustStdDev = Math.max(MIN_MAD_FLOOR, mad * NORMAL_CONSISTENCY_FACTOR);

        double robustZScore = (currentDelta - median) / robustStdDev;
        double hurdle = tierInfo.zScoreThreshold();

        ChatFirepowerStatus status = (robustZScore >= hurdle)
            ? ChatFirepowerStatus.PEAK
            : ChatFirepowerStatus.NORMAL;

        if (status == ChatFirepowerStatus.PEAK) {
            logPeakDetection(streamId, tierInfo, robustZScore, currentDelta, median, robustStdDev);
        }

        return new DetectionResult(status, currentDelta);
    }

    private double calculateMedian(List<Long> data) {
        List<Long> sorted = new ArrayList<>(data);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    private double calculateMad(List<Long> data, double median) {
        List<Double> absoluteDeviations = new ArrayList<>(data.size());
        for (Long value : data) {
            absoluteDeviations.add(Math.abs(value - median));
        }
        Collections.sort(absoluteDeviations);
        int size = absoluteDeviations.size();
        if (size % 2 == 1) {
            return absoluteDeviations.get(size / 2);
        }
        return (absoluteDeviations.get(size / 2 - 1) + absoluteDeviations.get(size / 2)) / 2.0;
    }

    private void logPeakDetection(String streamId, StreamTierInfo tierInfo, double robustZScore, long currentDelta, double median, double robustStdDev) {
        log.info("[PEAK 감지 - Robust MAD] 스트림: {}, 체급: {}, Z-Score: {} (허들: {}), 화력: {} (바닥값: {}), 중앙값: {}, MAD편차: {}",
            streamId, tierInfo.tier().name(), String.format("%.2f", robustZScore), tierInfo.zScoreThreshold(),
            currentDelta, tierInfo.noiseFloor(), String.format("%.2f", median), String.format("%.2f", robustStdDev));
    }
}
