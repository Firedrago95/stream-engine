package io.slice.stream.apiserver.streamer.application.scheduler;

import io.slice.stream.apiserver.streamer.application.StreamerSimilarityBatchService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamerSimilarityScheduler {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final StreamerSimilarityBatchService similarityBatchService;

    @Scheduled(cron = "${streamer.similarity.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void runSimilarityBatch() {
        LocalDate today = LocalDate.now(KST_ZONE);
        log.info("[Scheduler] 스트리머 시청자 유사도 새벽 정기 배치 시작 - 기준일: {}", today);
        try {
            similarityBatchService.executeBatch(today);
            log.info("[Scheduler] 스트리머 시청자 유사도 새벽 정기 배치 완료 - 기준일: {}", today);
        } catch (Exception e) {
            log.error("[Scheduler] 스트리머 시청자 유사도 배치 실행 중 예외 발생 - 기준일: {}", today, e);
        }
    }
}
