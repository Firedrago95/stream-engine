package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.application.service.AnalysisSignalBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisSignalBufferScheduler {

    private final AnalysisSignalBufferService bufferService;

    @Scheduled(fixedRate = 60_000)
    public void flushBufferedSignals() {
        try {
            int flushedCount = bufferService.flushAndPersist();
            if (flushedCount > 0) {
                log.info("[Signal-Buffer-Scheduler] 분석 신호 버퍼 정기 플러시 완료 - 집계 건수: {}", flushedCount);
            }
        } catch (Exception e) {
            log.error("[Signal-Buffer-Scheduler] 분석 신호 버퍼 플러시 중 오류 발생", e);
        }
    }
}
