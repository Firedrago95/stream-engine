package io.slice.stream.apiserver.analysis.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionService {

    private final JpaHighlightEventRepository repository;
    private final HighlightProperties properties;

    private Cache<String, Long> nmsCache;

    @PostConstruct
    public void init() {
        nmsCache = Caffeine.newBuilder()
            .expireAfterWrite(properties.cooldown().getSeconds(), TimeUnit.SECONDS)
            .build();
    }

    @Retryable(
        maxRetries = 2,
        delay = 1000
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSignal(AnalysisSignal signal) {
        if (signal.firepower() < properties.minimum()) return;

        String streamId = signal.streamId();

        if ("PEAK".equals(signal.status())) {
            processPeakSignal(signal, streamId);
        } else {
            processNormalSignal(signal, streamId);
        }
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cleanUpZombieSessions() {
        // 3분 전 시간 계산
        Instant zombieThreshold = Instant.now().minus(Duration.ofMinutes(3));

        List<HighlightEventEntity> zombies = repository.findZombieSessions(zombieThreshold);

        for (HighlightEventEntity session : zombies) {
            Instant finalEndTime = session.getLastPeakTime().plus(properties.trailingBuffer());
            long lastOffset = session.getLastPeakOffset() != null ? session.getLastPeakOffset() : 0L;

            session.finish(finalEndTime, lastOffset + properties.trailingBuffer().toMillis());
            log.info("[Session-Cleanup] 방치된 좀비 하이라이트 세션 강제 종료 Stream: {}", session.getStreamId());
        }
    }

    private void processPeakSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.get(streamId, k -> {
            // 캐시에 없으면 새 피크로 DB업데이트 후 값 반환
            updateDbSessionForPeak(signal, streamId);
            return signal.firepower();
        });

        if (cachedMaxFirepower != null && !cachedMaxFirepower.equals(signal.firepower())) {
            long threshold = (long) (cachedMaxFirepower * properties.extensionRatio());

            if (signal.firepower() > threshold) {
                // 임계치 통과시 캐시 갱신 및 세션 연장
                long newMax = Math.max(signal.firepower(), cachedMaxFirepower);
                nmsCache.put(streamId, newMax);
                updateDbSessionForPeak(signal, streamId);
            } else {
                log.debug("[Session-NMS] 피크 억제됨 (쿨다운 진행중) - Stream: {}, Firepower: {} <= Threshold: {}",
                    streamId, signal.firepower(), threshold);
            }
        }
    }

    private void updateDbSessionForPeak(AnalysisSignal signal, String streamId) {
        Optional<HighlightEventEntity> ongoingSession =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING");

        ongoingSession.ifPresentOrElse(
            session -> extendSession(signal, session),
            () -> startNewSession(signal)
        );
    }

    private void extendSession(AnalysisSignal signal, HighlightEventEntity session) {
        long safeOffset = signal.offsetMs() != null ? signal.offsetMs() : 0L;
        session.updatePeakFirepower(signal.firepower());
        session.updateLastPeak(signal.timestamp(), safeOffset);
        log.info("[Session-Extend] 하이라이트 세션 연장 Stream: {}, Peak: {}", signal.streamId(), signal.firepower());
    }

    private void startNewSession(AnalysisSignal signal) {
        // 실제 피크가 터진 시간에서 leadingBuffer를 빼서 영상 시작점을 앞으로 당김
        Instant adjustedStart = signal.timestamp().minus(properties.leadingBuffer());

        long sateOffset = signal.offsetMs() != null ? signal.offsetMs() : 0L;
        long startTimeOffset = Math.max(0L, sateOffset - properties.leadingBuffer().toMillis());

        HighlightEventEntity newSession = new HighlightEventEntity(
            signal.streamId(),
            signal.sessionId(),
            adjustedStart,
            startTimeOffset,
            signal.timestamp(), // 최초 피크 시간
            sateOffset,
            signal.firepower()
        );

        repository.save(newSession);
        log.info("[Session-Start] 하이라이트 세션 시작 Stream: {}, StartAt: {}", signal.streamId(), adjustedStart);
    }

    private void processNormalSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.getIfPresent(streamId);

        if (cachedMaxFirepower != null) {
            // 아직 쿨다운 기간 중, DB조회를 생략하고 즉시 종료
            return;
        }

        // 쿨다운 끝난 상태에 NORMAL, 진행중인 세션 확인하고 종료 검토
        repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING")
            .ifPresent(session -> checkAndFinishSession(signal, session));
    }

    private void checkAndFinishSession(AnalysisSignal signal, HighlightEventEntity session) {
        // 세션 닫기를 판단하는 억제 기준선은 마지막 피크 시간 + cooldown
        Instant threshold = session.getLastPeakTime().plus(properties.cooldown());

        // 현재시간이 기준을 넘으면 (쿨다운 동안 새로운 피크 없었다면 세션 확정)
        if (!signal.timestamp().isBefore(threshold)) {
            // 실제 저장될 하이라이트 종료시간은 마지막 피크 + trailingBuffer
            Instant finalEndTime = session.getLastPeakTime().plus(properties.trailingBuffer());
            long lastOffset = session.getLastPeakOffset() != null ? session.getLastPeakOffset() : 0L;
            long endTimeOffset = lastOffset + properties.trailingBuffer().toMillis();
            session.finish(finalEndTime, endTimeOffset);

            log.info("[Session-Finish] 하이라이트 세션 종료 Stream:{}, Duration: {}",
                signal.streamId(), Duration.between(session.getStartTime(), finalEndTime));
        }
    }

}
