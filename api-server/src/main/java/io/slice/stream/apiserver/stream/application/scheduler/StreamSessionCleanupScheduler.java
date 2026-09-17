package io.slice.stream.apiserver.stream.application.scheduler;

import io.slice.stream.apiserver.stream.application.StreamSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamSessionCleanupScheduler {

    private final StreamSessionService streamSessionService;

    @Scheduled(fixedDelay = 60_000)
    public void scheduleOfflineSessionCleanup() {
        log.info("[Session-Cleanup-Scheduler] 정기 오프라인 세션 정리 작업 시작");
        streamSessionService.closeOfflineSessions();
        log.info("[Session-Cleanup-Scheduler] 정기 오프라인 세션 정리 작업 완료");
    }
}
