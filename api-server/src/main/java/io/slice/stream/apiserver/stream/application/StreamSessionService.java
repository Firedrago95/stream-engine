package io.slice.stream.apiserver.stream.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSessionSummaryRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class StreamSessionService {

    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamRepository streamRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaViewMetricTimelineRepository timelineRepository;
    private final CacheManager cacheManager;
    private final Counter zombieSessionsClosedCounter;

    public StreamSessionService(
        JpaStreamSessionRepository sessionRepository,
        JpaStreamRepository streamRepository,
        JpaStreamSessionSegmentRepository segmentRepository,
        JpaViewMetricTimelineRepository timelineRepository,
        CacheManager cacheManager,
        MeterRegistry meterRegistry
    ) {
        this.sessionRepository = sessionRepository;
        this.streamRepository = streamRepository;
        this.segmentRepository = segmentRepository;
        this.timelineRepository = timelineRepository;
        this.cacheManager = cacheManager;
        this.zombieSessionsClosedCounter = Counter.builder("apiserver.zombie.sessions.closed")
            .description("마감 처리된 오프라인 세션 누적 수")
            .register(meterRegistry);
    }

    @Transactional
    public void updateSessionSegment(List<ChangedStreamRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<String> streamIds = requests.stream()
            .map(ChangedStreamRequest::streamId)
            .toList();

        List<StreamSessionEntity> activeSessions = sessionRepository.findAllActiveSessions(streamIds);
        if (activeSessions.isEmpty()) return;

        Map<String, StreamSessionEntity> sessionMap = activeSessions.stream()
            .collect(Collectors.toMap(StreamSessionEntity::getStreamId, s -> s));

        List<String> sessionIds = activeSessions.stream()
            .map(StreamSessionEntity::getSessionId)
            .toList();

        List<StreamSessionSegmentEntity> activeSegments = segmentRepository.findAllActiveSegments(sessionIds);
        Map<String, StreamSessionSegmentEntity> segmentMap = activeSegments.stream()
            .collect(Collectors.toMap(
                StreamSessionSegmentEntity::getSessionId, 
                s -> s, 
                (existing, replacement) -> existing
            ));

        List<StreamSessionSegmentEntity> segmentsToSave = new ArrayList<>();
        for (ChangedStreamRequest req : requests) {
            StreamSessionEntity session = sessionMap.get(req.streamId());
            if (session == null || !session.getSessionId().equals(req.liveId())) continue;

            StreamSessionSegmentEntity activeSegment = segmentMap.get(session.getSessionId());
            processSegmentUpdate(req, session, activeSegment)
                .ifPresent(segmentsToSave::add);
        }

        if (!segmentsToSave.isEmpty()) {
            segmentRepository.saveAll(segmentsToSave);
        }
    }

    private Optional<StreamSessionSegmentEntity> processSegmentUpdate(
        ChangedStreamRequest req,
        StreamSessionEntity session,
        StreamSessionSegmentEntity activeSegment
    ) {
        if (Objects.equals(req.newCategory(), session.getCategoryName()) &&
            Objects.equals(req.newTitle(), session.getTitle())) {
            return Optional.empty();
        }

        if (activeSegment != null) {
            activeSegment.endSegment(req.changedAt(), req.changeOffsetMs());
        }

        session.updateMetadata(req.newTitle(), req.newCategory());

        return Optional.of(new StreamSessionSegmentEntity(
            req.streamId(),
            session.getSessionId(),
            req.newTitle(),
            req.newCategory(),
            req.changedAt(),
            req.changeOffsetMs()
        ));
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void closeOfflineSessions() {
        Instant offlineThreshold = Instant.now().minus(Duration.ofHours(24));
        List<StreamSessionEntity> sessionsToClose = sessionRepository.findSessionsToClose(offlineThreshold);

        if (!sessionsToClose.isEmpty()) {
            zombieSessionsClosedCounter.increment(sessionsToClose.size());
        }

        List<String> streamIds = sessionsToClose.stream()
            .map(StreamSessionEntity::getStreamId)
            .distinct()
            .toList();

        Map<String, Instant> streamLastUpdateMap = streamRepository.findAllByStreamIdIn(streamIds).stream()
            .collect(Collectors.toMap(StreamEntity::getStreamId, StreamEntity::getLastUpdateAt, (existing, replacement) -> existing));

        for (StreamSessionEntity session : sessionsToClose) {
            Double avgViewers = timelineRepository.findAverageViewerCountBySessionId(session.getSessionId());
            Integer peakViewers = timelineRepository.findPeakViewerCountBySessionId(session.getSessionId());
            int finalPeak = peakViewers != null ? Math.max(peakViewers, session.getPeakViewers()) : session.getPeakViewers();

            Instant endedAt = streamLastUpdateMap.getOrDefault(session.getStreamId(), session.getStartedAt());

            session.finishSession(endedAt, finalPeak, avgViewers);

            segmentRepository.findActiveSegment(session.getSessionId())
                .ifPresent(segment -> {
                    long endOffset = Duration.between(session.getStartedAt(), endedAt).toMillis();
                    segment.endSegment(endedAt, endOffset);
                });

            evictActiveSessionAfterCommit(session.getStreamId());
            log.info("[Session-Manager] 방송 종료 감지, 세션 마감 - Stream: {}, SessionId: {}", session.getStreamId(), session.getSessionId());
        }

        streamRepository.markAllOfflineBefore(offlineThreshold);
    }

    @Transactional
    public void updateSessionSummary(String streamId, StreamSessionSummaryRequest summaries) {
        Optional<StreamSessionEntity> sessionOpt = sessionRepository.findActiveSession(streamId, summaries.liveId());
        if (sessionOpt.isEmpty()) {
            sessionOpt = sessionRepository.findBySessionId(summaries.liveId());
        }

        if (sessionOpt.isEmpty()) {
            log.warn("[Session-Manager] 종료 요약을 처리할 세션을 찾을 수 없습니다. Stream: {}, LiveId: {}", streamId, summaries.liveId());
            return;
        }

        StreamSessionEntity session = sessionOpt.get();
        session.updateSubscriberChatRatio(summaries.subscriberChatRatio());

        if (session.getEndedAt() == null) {
            Double avgViewers = timelineRepository.findAverageViewerCountBySessionId(session.getSessionId());
            Integer peakViewers = timelineRepository.findPeakViewerCountBySessionId(session.getSessionId());
            int finalPeak = peakViewers != null ? Math.max(peakViewers, session.getPeakViewers()) : session.getPeakViewers();

            Instant validEndedAt = normalizeEndedAt(summaries.endedAt(), session.getStartedAt());
            session.finishSession(validEndedAt, finalPeak, avgViewers);

            segmentRepository.findActiveSegment(session.getSessionId())
                .ifPresent(segment -> {
                    long endOffset = Math.max(0L, Duration.between(session.getStartedAt(), validEndedAt).toMillis());
                    segment.endSegment(validEndedAt, endOffset);
                });
        }

        streamRepository.findByStreamId(streamId)
            .ifPresent(StreamEntity::markOffline);

        evictActiveSessionAfterCommit(streamId);
    }

    private Instant normalizeEndedAt(Instant requestEndedAt, Instant sessionStartedAt) {
        if (requestEndedAt == null || requestEndedAt.isBefore(sessionStartedAt)) {
            log.warn("[Session-Manager] 유효하지 않은 방종 시각 수신 (requestEndedAt: {}, startedAt: {}). 서버 시각으로 보정합니다.",
                requestEndedAt, sessionStartedAt);
            Instant now = Instant.now();
            return now.isAfter(sessionStartedAt) ? now : sessionStartedAt;
        }
        return requestEndedAt;
    }

    private void evictActiveSessionAfterCommit(String streamId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(streamId);
                }
            });
        } else {
            doEvict(streamId);
        }
    }

    private void doEvict(String streamId) {
        Cache activeSessionsCache = cacheManager.getCache("activeSessions");
        if (activeSessionsCache != null) {
            activeSessionsCache.evict(streamId);
        }
    }
}
