package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.application.service.HighlightSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HighlightZombieSessionScheduler {

    private final HighlightSessionService highlightSessionService;

    @Scheduled(fixedRate = 60_000)
    public void scheduleZombieSessionCleanup() {
        highlightSessionService.cleanUpZombieSessions();
    }
}
