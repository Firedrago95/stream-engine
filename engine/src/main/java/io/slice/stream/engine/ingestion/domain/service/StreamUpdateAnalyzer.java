package io.slice.stream.engine.ingestion.domain.service;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StreamUpdateAnalyzer {

    public StreamUpdateResults analyze(
        List<StreamTarget> currentTargets,
        Set<String> activeChannelIds,
        List<StreamTarget> oldTargets,
        Instant changedAt
    ) {
        Map<String, StreamTarget> oldTargetMap = oldTargets.stream()
            .collect(toMap(StreamTarget::channelId, target -> target, (existing, replacing) -> existing));
        Map<String, StreamTarget> currentTargetMap = currentTargets.stream()
            .collect(toMap(StreamTarget::channelId, target -> target, (existing, replacing) -> existing));

        Set<StreamTarget> newStreams = filterNewStreams(currentTargets, oldTargetMap);
        Set<StreamTarget> closedStreamIds = filterClosedStreams(oldTargets, currentTargetMap);
        Set<ChangedStream> changedStreams = detectChangedStreams(currentTargets, oldTargetMap, changedAt);

        return new StreamUpdateResults(newStreams, closedStreamIds, changedStreams, changedAt);
    }

    private Set<StreamTarget> filterNewStreams(List<StreamTarget> currentTargets, Map<String, StreamTarget> oldTargetMap) {
        return currentTargets.stream()
            .filter(target -> {
                StreamTarget oldTarget = oldTargetMap.get(target.channelId());
                return oldTarget == null || oldTarget.liveId() != target.liveId();
            })
            .collect(toSet());
    }

    private Set<StreamTarget> filterClosedStreams(List<StreamTarget> oldTargets, Map<String, StreamTarget> currentTargetMap) {
        return oldTargets.stream()
            .filter(oldTarget -> {
                StreamTarget currentTarget = currentTargetMap.get(oldTarget.channelId());
                return currentTarget == null || currentTarget.liveId() != oldTarget.liveId();
            })
            .collect(toSet());
    }

    private Set<ChangedStream> detectChangedStreams(List<StreamTarget> currentTargets, Map<String, StreamTarget> oldTargetMap, Instant changedAt) {
        Set<ChangedStream> changedStreams = new HashSet<>();
        for (StreamTarget newTarget : currentTargets) {
            StreamTarget oldTarget = oldTargetMap.get(newTarget.channelId());
            if (oldTarget != null && oldTarget.liveId() == newTarget.liveId() && isMetadataChanged(oldTarget, newTarget)) {
                changedStreams.add(createChangedStream(oldTarget, newTarget, changedAt));
            }
        }
        return changedStreams;
    }

    private boolean isMetadataChanged(StreamTarget oldTarget, StreamTarget newTarget) {
        return !oldTarget.liveTitle().equals(newTarget.liveTitle()) ||
            !oldTarget.categoryName().equals(newTarget.categoryName());
    }

    private ChangedStream createChangedStream(StreamTarget oldTarget, StreamTarget newTarget, Instant changedAt) {
        Long changedOffsetMs = Duration.between(newTarget.startedAt(), changedAt).toMillis();
        return new ChangedStream(
            newTarget.channelId(),
            String.valueOf(newTarget.liveId()),
            oldTarget.liveTitle(),
            newTarget.liveTitle(),
            oldTarget.categoryName(),
            newTarget.categoryName(),
            changedAt,
            changedOffsetMs
        );
    }
}
