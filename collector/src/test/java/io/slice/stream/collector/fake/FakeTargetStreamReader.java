package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.TargetStreamReader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeTargetStreamReader implements TargetStreamReader {

    private final Set<String> targetChannels = ConcurrentHashMap.newKeySet();
    private boolean throwOnRead = false;

    public void addTarget(String channelId) {
        targetChannels.add(channelId);
    }

    public void removeTarget(String channelId) {
        targetChannels.remove(channelId);
    }

    public void setTargets(Set<String> channels) {
        targetChannels.clear();
        targetChannels.addAll(channels);
    }

    public void setThrowOnRead(boolean throwOnRead) {
        this.throwOnRead = throwOnRead;
    }

    @Override
    public Set<String> getTargetChannels() {
        if (throwOnRead) {
            throw new IllegalStateException("[Redis 오류] 타겟 풀 조회 실패");
        }
        return Collections.unmodifiableSet(targetChannels);
    }
}
