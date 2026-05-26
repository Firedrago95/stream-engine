package io.slice.stream.engine.ingestion.domain.service;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StreamUpdateAnalyzer {

    public StreamUpdateResults analyze(
        List<StreamTarget> currentTargets,
        Set<String> activeChannelIds,
        List<StreamTarget> oldTargets
    ) {
        Set<StreamTarget> newStreams = filterNewStreams(currentTargets, activeChannelIds);
        Set<String> closedStreamIds = filterClosedStreams(currentTargets, activeChannelIds);
        Set<ChangedStream> changedStreams = detectChangedStreams(currentTargets, oldTargets);

        return new StreamUpdateResults(newStreams, closedStreamIds, changedStreams);
    }

    private Set<StreamTarget> filterNewStreams(List<StreamTarget> currentTargets, Set<String> activeChannelIds) {
        return currentTargets.stream()
            .filter(target -> !activeChannelIds.contains(target.channelId()))
            .collect(toSet());
    }

    private Set<String> filterClosedStreams(List<StreamTarget> currentTargets, Set<String> activeChannelIds) {
        Set<String> currentChannelIds = currentTargets.stream()
            .map(StreamTarget::channelId)
            .collect(toSet());

        return activeChannelIds.stream()
            .filter(id -> !currentChannelIds.contains(id))
            .collect(toSet());
    }

    private Set<ChangedStream> detectChangedStreams(List<StreamTarget> currentTargets, List<StreamTarget> oldTargets) {
        Map<String, StreamTarget> oldTargetMap = oldTargets.stream()
            .collect(toMap(StreamTarget::channelId, target -> target));

        Set<ChangedStream> changedStreams = new HashSet<>();
        for (StreamTarget newTarget : currentTargets) {
            StreamTarget oldTarget = oldTargetMap.get(newTarget.channelId());
            if (oldTarget != null && isMetadataChanged(oldTarget, newTarget)) {
                changedStreams.add(createChangedStream(oldTarget, newTarget));
            }
        }
        return changedStreams;
    }

    private boolean isMetadataChanged(StreamTarget oldTarget, StreamTarget newTarget) {
        return !oldTarget.liveTitle().equals(newTarget.liveTitle()) ||
            !oldTarget.categoryName().equals(newTarget.categoryName());
    }

    private ChangedStream createChangedStream(StreamTarget oldTarget, StreamTarget newTarget) {
        return new ChangedStream(
            newTarget.channelId(),
            oldTarget.liveTitle(),
            newTarget.liveTitle(),
            oldTarget.categoryName(),
            newTarget.categoryName()
        );
    }
}
