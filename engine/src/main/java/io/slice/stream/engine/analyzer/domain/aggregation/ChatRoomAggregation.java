package io.slice.stream.engine.analyzer.domain.aggregation;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ChatRoomAggregation {

    private final String streamId;
    private final AtomicReference<Instant> lastChatTime;
    private final AtomicLong count;
    private final AtomicLong subscriberCount;
    private final AtomicReference<Set<Long>> userHashesRef;

    public ChatRoomAggregation(String streamId, Instant lastChatTime) {
        this.streamId = streamId;
        this.lastChatTime = new AtomicReference<>(lastChatTime);
        this.count = new AtomicLong(0);
        this.subscriberCount = new AtomicLong(0);
        this.userHashesRef = new AtomicReference<>(ConcurrentHashMap.newKeySet());
    }

    public void increaseCount(Instant eventTime, boolean isSubscriber) {
        increaseCount(eventTime, isSubscriber, null);
    }

    public void increaseCount(Instant eventTime, boolean isSubscriber, Long userHash) {
        count.incrementAndGet();
        if (isSubscriber) {
            subscriberCount.incrementAndGet();
        }
        if (userHash != null) {
            userHashesRef.get().add(userHash);
        }
        lastChatTime.updateAndGet(current ->
            (current == null || eventTime.isAfter(current)) ? eventTime : current
        );
    }

    public ChatDelta drainDelta() {
        long currentTotal = count.getAndSet(0);
        long currentSubscriber = subscriberCount.getAndSet(0);
        return new ChatDelta(currentTotal, currentSubscriber);
    }

    public void restoreDelta(ChatDelta delta) {
        if (delta == null || !delta.hasDelta()) {
            return;
        }
        count.addAndGet(delta.totalCount());
        subscriberCount.addAndGet(delta.subscriberCount());
    }

    public Set<Long> drainUserHashes() {
        Set<Long> previous = userHashesRef.getAndSet(ConcurrentHashMap.newKeySet());
        return Collections.unmodifiableSet(previous);
    }

    public void restoreUserHashes(Set<Long> restoredHashes) {
        if (restoredHashes != null && !restoredHashes.isEmpty()) {
            userHashesRef.get().addAll(restoredHashes);
        }
    }

    public String getStreamId() {
        return streamId;
    }

    public Instant getLastChatTime() {
        return lastChatTime.get();
    }

    public Long getCount() {
        return count.longValue();
    }

    public Long getSubscriberCount() {
        return subscriberCount.longValue();
    }
}
