package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TargetSyncEventListener {

    private final TargetStreamPool targetStreamPool;
    private final ApiServerClient apiServerClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initTargets() {
        syncTargets();
    }

    @Scheduled(cron = "${targeting.sync.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void syncTargetsPeriodically() {
        syncTargets();
    }

    private void syncTargets() {
        try {
            Optional<List<String>> targetsOpt = apiServerClient.fetchTargetChannels();
            targetsOpt.ifPresent(targets -> {
                targetStreamPool.syncTargets(new HashSet<>(targets));
                log.info("[타겟 동기화 완료] 총 {}개 채널 동기화됨", targets.size());
            });
        } catch (Exception e) {
            log.warn("[타겟 동기화 실패] API 서버 조회 실패: {}", e.getMessage());
        }
    }
}
