package io.slice.stream.apiserver.analysis.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionService {

    private final JpaHighlightEventRepository repository;

    @Value("${highlight.leading-buffer}")
    private Duration leadingBuffer;

    @Value("${highlight.trailing-buffer}")
    private Duration trailingBuffer;

    @Value("${highlight.cooldown}")
    private Duration cooldown;

    private Cache<String, Long> nmsCache;

    @PostConstruct
    public void init() {
        nmsCache = Caffeine.newBuilder()
            .expireAfterWrite(cooldown.getSeconds(), TimeUnit.SECONDS)
            .build();
    }

    @Retryable(
        maxRetries = 2,
        delay = 1000
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSignal(AnalysisSignal signal) {
        String streamId = signal.streamId();

        if ("PEAK".equals(signal.status())) {
            processPeakSignal(signal, streamId);
        } else {
            processNormalSignal(signal, streamId);
        }
    }

    private void processPeakSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.getIfPresent(streamId);

        if (cachedMaxFirepower == null) {
            // 캐시에 없다면, 쿨다운이 끝난 후의 새로운 피크거나, 방송의 첫 피크
            nmsCache.put(streamId, signal.firepower());
            updateDbSessionForPeak(signal, streamId);
        } else {
            // 캐시에 있다면, NMS 로직 적용
            if (signal.firepower() > cachedMaxFirepower) {
                // 비최댓값 억제 통과 : 쿨다운 중이더라도 기존보다 더 큰 피크라면 캐시와 db갱신
                nmsCache.put(streamId, signal.firepower());
                updateDbSessionForPeak(signal, streamId);
            } else {
                // 비최댓값 억제 작동: 더 작은 피크는 뇌절 방지를 위해 DB 조회를 생략하고 무시
                log.debug("[Session-NMS] 피크 억제됨 (쿨다운 진행중) - Stream: {}, Firepower: {} <= Max: {}",
                    streamId, signal.firepower(), cachedMaxFirepower);
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
        session.updatePeakFirepower(signal.firepower());
        session.updateLastPeak(signal.timestamp(), signal.offsetMs());
        log.info("[Session-Extend] 하이라이트 세션 연장 Stream: {}, Peak: {}", signal.streamId(), signal.firepower());
    }

    private void startNewSession(AnalysisSignal signal) {
        // 실제 피크가 터진 시간에서 leadingBuffer를 빼서 영상 시작점을 앞으로 당김
        Instant adjustedStart = signal.timestamp().minus(leadingBuffer);
        long startTimeOffset = Math.max(0L, signal.offsetMs() - leadingBuffer.toMillis());

        HighlightEventEntity newSession = new HighlightEventEntity(
            signal.streamId(),
            adjustedStart,
            startTimeOffset,
            signal.timestamp(), // 최초 피크 시간
            signal.offsetMs(),
            signal.firepower()
        );

        repository.save(newSession);
        log.info("[Session-Start] 하이라이트 세션 시작 Stream: {}, StartAt: {}", signal.streamId(), adjustedStart);
    }

    private void processNormalSignal(AnalysisSignal signal, String streamId) {
        Long cachedMaxFirepower = nmsCache.getIfPresent(streamId);

        if (cachedMaxFirepower != null) {
            // 아직 90초 쿨다운 기간 중, DB조회를 생략하고 즉시 종료
            return;
        }

        // 쿨다운 끝난 상태에 NORMAL, 진행중인 세션 확인하고 종료 검토
        repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING")
            .ifPresent(session -> checkAndFinishSession(signal, session));
    }

    private void checkAndFinishSession(AnalysisSignal signal, HighlightEventEntity session) {
        // 세션 닫기를 판단하는 억제 기준선은 마지막 피크 시간 + cooldown
        Instant threshold = session.getLastPeakTime().plus(cooldown);

        // 현재시간이 기준을 넘으면 (쿨다운 동안 새로운 피크 없었다면 세션 확정)
        if (!signal.timestamp().isBefore(threshold)) {
            // 실제 저장될 하이라이트 종료시간은 마지막 피크 + trailingBuffer
            Instant finalEndTime = session.getLastPeakTime().plus(trailingBuffer);
            long endTimeOffset = session.getLastPeakOffset() + trailingBuffer.toMillis();
            session.finish(finalEndTime, endTimeOffset);

            log.info("[Session-Finish] 하이라이트 세션 종료 Stream:{}, Duration: {}",
                signal.streamId(), Duration.between(session.getStartTime(), finalEndTime));
        }
    }

}
