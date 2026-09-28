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

    @Override
    public List<String> getActiveStreamIds() {
        return targets.stream().map(StreamTarget::channelId).toList();
    }

    @Override
    public List<StreamTarget> getActiveStreamTargets() {
        return List.copyOf(targets);
    }
}
