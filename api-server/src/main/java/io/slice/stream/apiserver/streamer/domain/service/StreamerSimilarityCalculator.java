package io.slice.stream.apiserver.streamer.domain.service;

import io.slice.stream.apiserver.global.config.StreamerSimilarityProperties;
import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StreamerSimilarityCalculator {

    private final StreamerSimilarityProperties properties;

    public StreamerSimilarityCalculator() {
        this(StreamerSimilarityProperties.defaultProperties());
    }

    public StreamerSimilarityCalculator(StreamerSimilarityProperties properties) {
        this.properties = (properties != null) ? properties : StreamerSimilarityProperties.defaultProperties();
    }

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
        if (baseChatters == null || baseChatters.size() < properties.minSampleSize()) {
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
        if (bestMatch.similarityPercent() < properties.minSimilarityThreshold()) {
            return new CalculationResult(SimilarityStatus.INDEPENDENT_FANDOM, Collections.emptyList());
        }

        List<SimilarityMatch> topMatches = candidates.stream()
            .filter(match -> match.similarityPercent() >= properties.minSimilarityThreshold())
            .limit(properties.topRankLimit())
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
