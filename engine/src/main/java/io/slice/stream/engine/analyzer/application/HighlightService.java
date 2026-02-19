package io.slice.stream.engine.analyzer.application;

import io.slice.stream.engine.analyzer.domain.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.HighlightSignalClient;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightService {

    private final ActiveStreamProvider streamProvider;
    private final ExecutorService virtualThreadExecutor;
    private final HighlightDetector detector;
    private final HighlightSignalClient signalClient;
    private final Clock clock;

    /**
    *  TODO: [Thundering Herd] 현재 모든 활성 스트림에 대해 동시에 Redis 조회를 요청하고 있음.
    *  향후 활성 스트림 수가 수천 개 이상으로 증가할 경우, Redis에 순간적인 과부하를 유발할 수 있음 (Thundering Herd 문제).
    *  스트림 수가 500개를 초과하는 시점에는 요청을 배치(Batch)로 나누어 처리하거나
    *  Guava의 RateLimiter와 같은 도구를 사용해 요청을 분산시키는 로직을 추가하는 것을 고려해야 함.
    **/
    @Scheduled(fixedRate = 3000)
    public void monitorHighlights() {
        List<String> activeStreamIds = streamProvider.getActiveStreamIds();

        // [방어 코드] 위 TODO 상황 감지
        if (activeStreamIds.size() > 500) {
            log.warn("[Thundering Herd Warning] 활성 스트림이 {}개입니다. Redis 부하 방지를 위해 위 TODO의 리팩토링을 진행해주세요.", activeStreamIds.size());
        }

        List<CompletableFuture<Optional<AnalysisSignal>>> futures = activeStreamIds.stream()
            .map(id -> CompletableFuture.supplyAsync(() -> processStream(id), virtualThreadExecutor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<AnalysisSignal> signals = futures.stream()
            .map(CompletableFuture::join)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (!signals.isEmpty()) {
            signalClient.send(signals);
        }
    }

    private Optional<AnalysisSignal> processStream(String streamId) {
        try {
            ChatFirepowerStatus currentStatus = detector.detect(streamId);
            if (currentStatus == ChatFirepowerStatus.WAITING) return Optional.empty();

            return Optional.of(new AnalysisSignal(streamId, currentStatus.name(), clock.instant()));
        } catch (Exception e) {
            log.error("스트림 분석 에러: {}", streamId, e);
            return Optional.empty();
        }
    }
}
