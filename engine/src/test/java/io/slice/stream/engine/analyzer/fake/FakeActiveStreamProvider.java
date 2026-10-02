package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import java.util.ArrayList;
import java.util.List;

public class FakeActiveStreamProvider implements ActiveStreamProvider {

    private final List<StreamTarget> targets = new ArrayList<>();

    public void setTargets(List<StreamTarget> targets) {
        this.targets.clear();
        if (targets != null) {
            this.targets.addAll(targets);
        }
    }

    public void setActiveStreamIds(List<String> streamIds) {
        this.targets.clear();
        if (streamIds != null) {
            for (String id : streamIds) {
                this.targets.add(new StreamTarget(id, id, id, 1L, id, 100, "", "", null));
            }
        }
    }

    @Override
    public List<String> getActiveStreamIds() {
        return targets.stream().map(StreamTarget::channelId).toList();
    }

    @Override
    public List<StreamTarget> getActiveStreamTargets() {
        return List.copyOf(targets);
    }
}
