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
        Set<StreamTarget> newStreams = filterNewStreams(currentTargets, activeChannelIds);
        Set<StreamTarget> closedStreamIds = filterClosedStreams(currentTargets, oldTargets);
        Set<ChangedStream> changedStreams = detectChangedStreams(currentTargets, oldTargets, changedAt);

        return new StreamUpdateResults(newStreams, closedStreamIds, changedStreams);
    }

    private Set<StreamTarget> filterNewStreams(List<StreamTarget> currentTargets, Set<String> activeChannelIds) {
        return currentTargets.stream()
            .filter(target -> !activeChannelIds.contains(target.channelId()))
            .collect(toSet());
    }

    private Set<StreamTarget> filterClosedStreams(List<StreamTarget> currentTargets, List<StreamTarget> oldTargets) {
        return oldTargets.stream()
            .filter(oldTarget -> !currentTargets.contains(oldTarget))
            .collect(Collectors.toSet());
    }

    private Set<ChangedStream> detectChangedStreams(List<StreamTarget> currentTargets, List<StreamTarget> oldTargets, Instant changedAt) {
        Map<String, StreamTarget> oldTargetMap = oldTargets.stream()
            .collect(toMap(StreamTarget::channelId, target -> target));

        Set<ChangedStream> changedStreams = new HashSet<>();
        for (StreamTarget newTarget : currentTargets) {
            StreamTarget oldTarget = oldTargetMap.get(newTarget.channelId());
            if (oldTarget != null && isMetadataChanged(oldTarget, newTarget)) {
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
            oldTarget.liveTitle(),
            newTarget.liveTitle(),
            oldTarget.categoryName(),
            newTarget.categoryName(),
            changedAt,
            changedOffsetMs
        );
    }
}
