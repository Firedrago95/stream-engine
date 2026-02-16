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

    @Scheduled(fixedRate = 3000)
    public void monitorHighlights() {
        List<String> activeStreamIds = streamProvider.getActiveStreamIds();

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
