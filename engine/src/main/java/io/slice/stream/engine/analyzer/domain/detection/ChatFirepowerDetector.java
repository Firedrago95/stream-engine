package io.slice.stream.engine.analyzer.domain.detection;

import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatFirepowerDetector implements HighlightDetector {

    private static final int MIN_DATA_POINTS_FOR_ANALYSIS = 10;
    private static final double STD_DEV_THRESHOLD = 0.0001;

    @Override
    public DetectionResult detect(String streamId, List<Long> deltas, StreamTierInfo tierInfo) {
        if (isDataInsufficient(deltas)) {
            return DetectionResult.waiting();
        }

        long currentDelta = deltas.getLast();

        // 1차 필터링: 동적 1% 임계치 미만인 경우 분석 제외
        if (currentDelta < tierInfo.minFirepowerCutoff()) {
            return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
        }

        return runZScoreAnalysis(streamId, deltas, tierInfo, currentDelta);
    }

    private boolean isDataInsufficient(List<Long> deltas) {
        return deltas == null || deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS;
    }

    private DetectionResult runZScoreAnalysis(String streamId, List<Long> deltas, StreamTierInfo tierInfo, long currentDelta) {
        // 현재 피크가 평균을 오염시키지 않도록 최근 틱들을 배제한 과거 데이터 추출
        int exclusionCount = tierInfo.maskingExclusionTicks() + 1;
        if (deltas.size() <= exclusionCount) {
            return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
        }

        List<Long> history = deltas.subList(0, deltas.size() - exclusionCount);
        double mean = history.stream().mapToLong(Long::valueOf).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(history, mean);

        // 표준편차가 0에 가까운 경우 단순 비교로 대체 (Divide by Zero 방지)
        if (stdDev < STD_DEV_THRESHOLD) {
            ChatFirepowerStatus status = (currentDelta > mean) ? ChatFirepowerStatus.PEAK : ChatFirepowerStatus.NORMAL;
            return new DetectionResult(status, currentDelta);
        }

        double zScore = (currentDelta - mean) / stdDev;
        ChatFirepowerStatus status = (zScore > tierInfo.zScoreThreshold()) ? ChatFirepowerStatus.PEAK : ChatFirepowerStatus.NORMAL;

        if (status == ChatFirepowerStatus.PEAK) {
            logPeakDetection(streamId, tierInfo, zScore, currentDelta, mean, stdDev);
        }

        return new DetectionResult(status, currentDelta);
    }

    private double calculateStandardDeviation(List<Long> data, double mean) {
        double variance = data.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private void logPeakDetection(String streamId, StreamTierInfo tierInfo, double zScore, long currentDelta, double mean, double stdDev) {
        log.info("[PEAK 감지] Stream: {}, 체급: {}, Z-Score: {} (허들: {}), 화력: {} (1%컷: {}), 평균: {}, 편차: {}",
            streamId, tierInfo.tier().name(), String.format("%.2f", zScore), tierInfo.zScoreThreshold(),
            currentDelta, tierInfo.minFirepowerCutoff(), String.format("%.2f", mean), String.format("%.2f", stdDev));
    }
}
