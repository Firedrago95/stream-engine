package io.slice.stream.engine.analyzer.domain.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatRoomAggregationTest {

    @Test
    void 채팅_인입_시_카운트와_구독자_카운트_및_마지막_채팅_시간이_갱신된다() {
        Instant time1 = Instant.parse("2026-09-24T10:00:00Z");
        Instant time2 = Instant.parse("2026-09-24T10:00:01Z");
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", Instant.EPOCH);

        aggregation.increaseCount(time1, false);
        aggregation.increaseCount(time2, true);

        assertAll(
            () -> assertThat(aggregation.getCount()).isEqualTo(2L),
            () -> assertThat(aggregation.getSubscriberCount()).isEqualTo(1L),
            () -> assertThat(aggregation.getLastChatTime()).isEqualTo(time2)
        );
    }

    @Test
    void drainDelta_호출_시_누적된_델타를_반환하고_로컬_카운터를_0으로_초기화한다() {
        Instant now = Instant.now();
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", Instant.EPOCH);

        aggregation.increaseCount(now, false);
        aggregation.increaseCount(now, true);
        aggregation.increaseCount(now, true);

        ChatDelta delta = aggregation.drainDelta();

        assertAll(
            () -> assertThat(delta.totalCount()).isEqualTo(3L),
            () -> assertThat(delta.subscriberCount()).isEqualTo(2L),
            () -> assertThat(delta.hasDelta()).isTrue(),
            () -> assertThat(aggregation.getCount()).isEqualTo(0L),
            () -> assertThat(aggregation.getSubscriberCount()).isEqualTo(0L)
        );
    }

    @Test
    void 채팅이_없을_때_drainDelta_호출_시_카운트가_0인_델타를_반환한다() {
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", Instant.EPOCH);

        ChatDelta delta = aggregation.drainDelta();

        assertAll(
            () -> assertThat(delta.totalCount()).isEqualTo(0L),
            () -> assertThat(delta.subscriberCount()).isEqualTo(0L),
            () -> assertThat(delta.hasDelta()).isFalse()
        );
    }

    @Test
    void drainDelta_이후_새로운_채팅이_들어오면_새로운_델타만_수집된다() {
        Instant now = Instant.now();
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", Instant.EPOCH);

        aggregation.increaseCount(now, false);
        aggregation.increaseCount(now, true);
        aggregation.drainDelta();

        aggregation.increaseCount(now.plusSeconds(1), false);
        ChatDelta secondDelta = aggregation.drainDelta();

        assertAll(
            () -> assertThat(secondDelta.totalCount()).isEqualTo(1L),
            () -> assertThat(secondDelta.subscriberCount()).isEqualTo(0L),
            () -> assertThat(secondDelta.hasDelta()).isTrue()
        );
    }

    @Test
    void 멀티스레드_환경에서_동시에_채팅_인입과_drainDelta가_발생해도_데이터_유실이_없다() throws InterruptedException {
        ChatRoomAggregation aggregation = new ChatRoomAggregation("stream1", Instant.EPOCH);
        int threadCount = 10;
        int messagesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong drainedTotal = new AtomicLong(0);
        AtomicLong drainedSub = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        aggregation.increaseCount(Instant.now(), true);
                        if (j % 20 == 0) {
                            ChatDelta delta = aggregation.drainDelta();
                            drainedTotal.addAndGet(delta.totalCount());
                            drainedSub.addAndGet(delta.subscriberCount());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        ChatDelta finalDelta = aggregation.drainDelta();
        long totalSum = drainedTotal.get() + finalDelta.totalCount();
        long subSum = drainedSub.get() + finalDelta.subscriberCount();

        assertThat(totalSum).isEqualTo(threadCount * messagesPerThread);
        assertThat(subSum).isEqualTo(threadCount * messagesPerThread);
    }
}
