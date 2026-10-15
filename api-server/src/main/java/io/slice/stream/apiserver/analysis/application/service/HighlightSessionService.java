package io.slice.stream.apiserver.analysis.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionService {

    private final HighlightSessionPersistenceService persistenceService;
    private final JpaHighlightEventRepository repository;
    private final HighlightProperties properties;

    private final Set<String> activeSessionStreams = ConcurrentHashMap.newKeySet();
    private Cache<String, Long> nmsCache;

    @PostConstruct
    public void init() {
        nmsCache = Caffeine.newBuilder()
            .expireAfterWrite(properties.cooldown().getSeconds(), TimeUnit.SECONDS)
            .build();
        activeSessionStreams.addAll(repository.findOngoingStreamIds());
        log.info("[Highlight] 활성 하이라이트 세션 스트림 캐시 초기화 완료 - {}건", activeSessionStreams.size());
    }

    @Retryable(
        maxRetries = 2,
        delay = 1000
    )
    public void handleSignal(AnalysisSignal signal) {
        String streamId = signal.streamId();

        if ("PEAK".equals(signal.status())) {
            if (signal.firepower() < properties.minimum()) {
                return;
            }
            processPeakSignal(signal, streamId);
        } else {
            processNormalSignal(signal, streamId);
        }
    }

    public void cleanUpZombieSessions() {
        Instant zombieThreshold = Instant.now().minus(Duration.ofMinutes(3));
        List<String> finishedStreamIds = persistenceService.cleanUpZombieSessions(zombieThreshold, properties.trailingBuffer());
        activeSessionStreams.removeAll(finishedStreamIds);
    }

    private void processPeakSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.getIfPresent(streamId);
        if (cachedMaxFirepower == null) {
            persistenceService.updateDbSessionForPeak(signal, properties.leadingBuffer());
            activeSessionStreams.add(streamId);
            nmsCache.put(streamId, signal.firepower());
            return;
        }

        long threshold = (long) (cachedMaxFirepower * properties.extensionRatio());
        if (signal.firepower() >= threshold) {
            long newMax = Math.max(signal.firepower(), cachedMaxFirepower);
            nmsCache.put(streamId, newMax);
            persistenceService.updateDbSessionForPeak(signal, properties.leadingBuffer());
            activeSessionStreams.add(streamId);
        } else {
            log.debug("[Session-NMS] 피크 억제됨 (쿨다운 진행중) - Stream: {}, Firepower: {} < Threshold: {}",
                streamId, signal.firepower(), threshold);
        }
    }

    private void processNormalSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.getIfPresent(streamId);
        if (cachedMaxFirepower != null) {
            return;
        }

        if (!activeSessionStreams.contains(streamId)) {
            return;
        }

        boolean finished = persistenceService.finishSessionIfEligible(
            signal, properties.cooldown(), properties.trailingBuffer()
        );
        if (finished) {
            activeSessionStreams.remove(streamId);
        }
    }
}
