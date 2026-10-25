package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.application.service.HighlightSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HighlightZombieSessionScheduler {

    private final HighlightSessionService highlightSessionService;

    public void scheduleZombieSessionCleanup() {
        highlightSessionService.cleanUpZombieSessions();
    }
}
