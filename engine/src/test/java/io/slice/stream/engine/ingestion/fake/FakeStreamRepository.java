package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeStreamRepository implements StreamRepository {

    private final Map<String, StreamTarget> targets = new ConcurrentHashMap<>();
    private final Set<StreamTarget> lastClosedStreams = new HashSet<>();
    private final List<StreamTarget> lastSyncedTargets = new ArrayList<>();

    public void setActiveTargets(List<StreamTarget> list) {
        targets.clear();
        if (list != null) {
            for (StreamTarget target : list) {
                targets.put(target.channelId(), target);
            }
        }
    }

    public void addActiveTarget(StreamTarget target) {
        targets.put(target.channelId(), target);
    }

    @Override
    public Set<String> getActiveChannelIds() {
        return new HashSet<>(targets.keySet());
    }

    @Override
    public List<StreamTarget> getStreamTargets(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyList();
        }
        return channelIds.stream()
            .map(targets::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void sync(Set<StreamTarget> closedStreams, List<StreamTarget> activeTargets) {
        lastClosedStreams.clear();
        if (closedStreams != null) {
            lastClosedStreams.addAll(closedStreams);
            for (StreamTarget closed : closedStreams) {
                targets.remove(closed.channelId());
            }
        }

        lastSyncedTargets.clear();
        if (activeTargets != null) {
            lastSyncedTargets.addAll(activeTargets);
            for (StreamTarget active : activeTargets) {
                targets.put(active.channelId(), active);
            }
        }
    }

    public List<StreamTarget> getLastSyncedTargets() {
        return Collections.unmodifiableList(lastSyncedTargets);
    }

    public Set<StreamTarget> getLastClosedStreams() {
        return Collections.unmodifiableSet(lastClosedStreams);
    }

    @Override
    public void updatePaidPromotion(String channelId, boolean paidPromotion) {
        StreamTarget existing = targets.get(channelId);
        if (existing != null) {
            targets.put(channelId, existing.withPaidPromotion(paidPromotion));
        }
    }
}
