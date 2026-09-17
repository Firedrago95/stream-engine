package io.slice.stream.apiserver.stream.targeting.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetStreamerSchedulerTest {

    @Test
    void 새벽_정기_타겟_채널_캐시_초기화_메서드가_예외_없이_실행된다() {
        TargetStreamerScheduler scheduler = new TargetStreamerScheduler();

        assertDoesNotThrow(scheduler::evictTargetChannelsCache);
    }
}
