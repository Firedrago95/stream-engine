package io.slice.stream.apiserver.streamer.domain.service;

import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamerSimilarityCalculator {

    public static final int MIN_SAMPLE_SIZE = 100;
    public static final double MIN_SIMILARITY_THRESHOLD = 3.0;
    public static final int TOP_RANK_LIMIT = 3;

    public record SimilarityMatch(
        String targetStreamId,
        double similarityPercent,
        int commonChatterCount,
        int totalChatterCount
    ) {
    }

    public record CalculationResult(
        SimilarityStatus status,
        List<SimilarityMatch> matches
    ) {
    }

    public CalculationResult calculate(
        String baseStreamId,
        Map<String, Set<Long>> allStreamerChatters
    ) {
        Set<Long> baseChatters = allStreamerChatters.get(baseStreamId);
        if (baseChatters == null || baseChatters.size() < MIN_SAMPLE_SIZE) {
            return new CalculationResult(SimilarityStatus.INSUFFICIENT_DATA, Collections.emptyList());
        }

        List<SimilarityMatch> candidates = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> entry : allStreamerChatters.entrySet()) {
            String targetStreamId = entry.getKey();
            Set<Long> targetChatters = entry.getValue();

            if (targetStreamId.equals(baseStreamId) || targetChatters == null || targetChatters.isEmpty()) {
                continue;
            }

            SimilarityMatch match = calculateSimilarityMatch(baseChatters, targetStreamId, targetChatters);
            candidates.add(match);
        }

        if (candidates.isEmpty()) {
            return new CalculationResult(SimilarityStatus.INDEPENDENT_FANDOM, Collections.emptyList());
        }

        candidates.sort(Comparator.comparingDouble(SimilarityMatch::similarityPercent).reversed());

        SimilarityMatch bestMatch = candidates.getFirst();
        if (bestMatch.similarityPercent() < MIN_SIMILARITY_THRESHOLD) {
            return new CalculationResult(SimilarityStatus.INDEPENDENT_FANDOM, Collections.emptyList());
        }

        List<SimilarityMatch> topMatches = candidates.stream()
            .filter(match -> match.similarityPercent() >= MIN_SIMILARITY_THRESHOLD)
            .limit(TOP_RANK_LIMIT)
            .toList();

        return new CalculationResult(SimilarityStatus.NORMAL, topMatches);
    }

    private SimilarityMatch calculateSimilarityMatch(
        Set<Long> baseChatters,
        String targetStreamId,
        Set<Long> targetChatters
    ) {
        int commonCount = countCommonChatters(baseChatters, targetChatters);
        int totalCount = baseChatters.size() + targetChatters.size() - commonCount;

        if (totalCount == 0) {
            return new SimilarityMatch(targetStreamId, 0.0, 0, 0);
        }

        double rawPercent = ((double) commonCount / totalCount) * 100.0;
        double roundedPercent = Math.round(rawPercent * 100.0) / 100.0;

        return new SimilarityMatch(targetStreamId, roundedPercent, commonCount, totalCount);
    }

    private int countCommonChatters(Set<Long> setA, Set<Long> setB) {
        Set<Long> smaller = (setA.size() <= setB.size()) ? setA : setB;
        Set<Long> larger = (smaller == setA) ? setB : setA;

        int count = 0;
        for (Long element : smaller) {
            if (larger.contains(element)) {
                count++;
            }
        }
        return count;
    }
}
