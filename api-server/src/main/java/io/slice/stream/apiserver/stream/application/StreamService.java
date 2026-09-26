package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamRepository streamRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaViewMetricTimelineRepository timelineRepository;
    private final CacheManager cacheManager;


    @Transactional
    public void syncAll(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Instant currentTime = Instant.now();
        Map<String, StreamSyncRequest> uniqueRequests = deduplicateRequests(requests);

        upsertLiveStreams(uniqueRequests, currentTime);

        Map<String, StreamSessionEntity> sessionMap = resolveSessions(uniqueRequests, currentTime);

        recordViewMetricTimelines(uniqueRequests, sessionMap, currentTime);

        log.info("[Sync] Native Upsert 완료 - {}건 (중복 제거 전: {}건)",
            uniqueRequests.size(), requests.size());
    }

    private Map<String, StreamSyncRequest> deduplicateRequests(List<StreamSyncRequest> requests) {
        return requests.stream()
            .collect(Collectors.toMap(
                StreamSyncRequest::streamId,
                req -> req,
                (existing, replacement) -> replacement
            ));
    }

    private void upsertLiveStreams(Map<String, StreamSyncRequest> requests, Instant currentTime) {
        for (StreamSyncRequest req : requests.values()) {
            StreamEntity entity = new StreamEntity(req.streamId(), req.streamerName());
            entity.heartbeat(
                req.streamerName(),
                req.liveTitle(),
                req.profileImageUrl(),
                req.categoryName(),
                req.concurrentUserCount()
            );

            streamRepository.upsertStream(entity, currentTime);
        }
    }

    private Map<String, StreamSessionEntity> resolveSessions(
        Map<String, StreamSyncRequest> uniqueRequests,
        Instant currentTime
    ) {
        Map<String, StreamSessionEntity> sessionMap = new HashMap<>();

        handleActiveSessions(uniqueRequests, sessionMap, currentTime);
        reopenClosedSessionsIfPresent(uniqueRequests, sessionMap, currentTime);
        createNewSessions(uniqueRequests, sessionMap, currentTime);

        return sessionMap;
    }

    private void handleActiveSessions(
        Map<String, StreamSyncRequest> uniqueRequests,
        Map<String, StreamSessionEntity> sessionMap,
        Instant currentTime
    ) {
        List<String> streamIds = new ArrayList<>(uniqueRequests.keySet());
        List<StreamSessionEntity> activeSessions = sessionRepository.findAllActiveSessions(streamIds);
        Map<String, StreamSessionEntity> activeSessionMap = activeSessions.stream()
            .collect(Collectors.toMap(StreamSessionEntity::getStreamId, s -> s, (a, b) -> a));

        for (StreamSyncRequest req : uniqueRequests.values()) {
            StreamSessionEntity activeSession = activeSessionMap.get(req.streamId());
            if (activeSession != null) {
                if (Objects.equals(activeSession.getSessionId(), req.liveId())) {
                    if (req.paidPromotion()) {
                        activeSession.markPaidPromotion();
                    }
                    sessionMap.put(req.streamId(), activeSession);
                } else {
                    closePreviousSession(activeSession, currentTime, req.liveId());
                }
            }
        }
    }

    private void closePreviousSession(StreamSessionEntity activeSession, Instant currentTime, String newLiveId) {
        Double avgViewers = timelineRepository.findAverageViewerCountBySessionId(activeSession.getSessionId());
        Integer peakViewers = timelineRepository.findPeakViewerCountBySessionId(activeSession.getSessionId());
        int finalPeak = peakViewers != null ? Math.max(peakViewers, activeSession.getPeakViewers()) : activeSession.getPeakViewers();
        activeSession.finishSession(currentTime, finalPeak, avgViewers);

        segmentRepository.findActiveSegment(activeSession.getSessionId())
            .ifPresent(segment -> {
                long endOffset = Duration.between(activeSession.getStartedAt(), currentTime).toMillis();
                segment.endSegment(currentTime, endOffset);
            });

        log.info("[Sync] 이전 세션 종료 (새 방송 감지) - Stream: {}, OldSession: {}, NewLiveId: {}, AvgViewers: {}",
            activeSession.getStreamId(), activeSession.getSessionId(), newLiveId, activeSession.getAverageViewerCount());
    }


    private void reopenClosedSessionsIfPresent(
        Map<String, StreamSyncRequest> uniqueRequests,
        Map<String, StreamSessionEntity> sessionMap,
        Instant currentTime
    ) {
        List<String> remainingLiveIds = uniqueRequests.values().stream()
            .filter(req -> !sessionMap.containsKey(req.streamId()))
            .map(StreamSyncRequest::liveId)
            .filter(Objects::nonNull)
            .toList();

        if (remainingLiveIds.isEmpty()) {
            return;
        }

        List<StreamSessionEntity> existingSessions = sessionRepository.findAllBySessionIdIn(remainingLiveIds);
        Map<String, StreamSessionEntity> existingSessionMap = existingSessions.stream()
            .collect(Collectors.toMap(StreamSessionEntity::getSessionId, s -> s, (a, b) -> a));

        List<StreamSessionSegmentEntity> activeSegments = segmentRepository.findAllActiveSegments(
            new ArrayList<>(existingSessionMap.keySet())
        );
        Set<String> activeSegmentSessionIds = activeSegments.stream()
            .map(StreamSessionSegmentEntity::getSessionId)
            .collect(Collectors.toSet());

        List<StreamSessionSegmentEntity> newSegments = new ArrayList<>();
        for (StreamSyncRequest req : uniqueRequests.values()) {
            if (sessionMap.containsKey(req.streamId()) || req.liveId() == null) {
                continue;
            }
            StreamSessionEntity existing = existingSessionMap.get(req.liveId());
            if (existing != null) {
                if (existing.getEndedAt() != null) {
                    existing.reopen();
                    log.info("[Sync] 오판 종료된 세션 재활성화 - Stream: {}, SessionId: {}",
                        existing.getStreamId(), existing.getSessionId());
                    evictActiveSessionAfterCommit(existing.getStreamId());
                }
                if (req.paidPromotion()) {
                    existing.markPaidPromotion();
                }
                sessionMap.put(req.streamId(), existing);

                if (!activeSegmentSessionIds.contains(existing.getSessionId())) {
                    Instant sessionStartedAt = req.startedAt() != null ? req.startedAt() : currentTime;
                    long startOffset = Duration.between(existing.getStartedAt(), currentTime).toMillis();
                    StreamSessionSegmentEntity segment = new StreamSessionSegmentEntity(
                        req.streamId(),
                        req.liveId(),
                        req.liveTitle(),
                        req.categoryName(),
                        sessionStartedAt,
                        Math.max(0L, startOffset)
                    );
                    newSegments.add(segment);
                    activeSegmentSessionIds.add(existing.getSessionId());
                }
            }
        }

        if (!newSegments.isEmpty()) {
            segmentRepository.saveAll(newSegments);
        }
    }

    private void createNewSessions(
        Map<String, StreamSyncRequest> uniqueRequests,
        Map<String, StreamSessionEntity> sessionMap,
        Instant currentTime
    ) {
        List<StreamSessionEntity> newSessions = new ArrayList<>();
        List<StreamSessionSegmentEntity> newSegments = new ArrayList<>();

        for (StreamSyncRequest req : uniqueRequests.values()) {
            if (!sessionMap.containsKey(req.streamId())) {
                Instant sessionStartedAt = req.startedAt() != null ? req.startedAt() : currentTime;
                StreamSessionEntity session = new StreamSessionEntity(
                    req.streamId(),
                    req.liveId(),
                    req.liveTitle(),
                    req.categoryName(),
                    sessionStartedAt,
                    req.paidPromotion()
                );
                newSessions.add(session);
                sessionMap.put(req.streamId(), session);

                StreamSessionSegmentEntity segment = new StreamSessionSegmentEntity(
                    req.streamId(),
                    req.liveId(),
                    req.liveTitle(),
                    req.categoryName(),
                    sessionStartedAt,
                    0L
                );
                newSegments.add(segment);
            }
        }

        if (!newSessions.isEmpty()) {
            sessionRepository.saveAll(newSessions);
            for (StreamSessionEntity s : newSessions) {
                evictActiveSessionAfterCommit(s.getStreamId());
            }
        }
        if (!newSegments.isEmpty()) {
            segmentRepository.saveAll(newSegments);
            log.info("[Sync] 신규 방송 세션/세그먼트 벌크 생성 완료 - 세션 {}건, 세그먼트 {}건",
                newSessions.size(), newSegments.size());
        }
    }

    private void recordViewMetricTimelines(
        Map<String, StreamSyncRequest> uniqueRequests,
        Map<String, StreamSessionEntity> sessionMap,
        Instant currentTime
    ) {
        List<ViewMetricTimelineEntity> timelineEntities = new ArrayList<>();
        for (StreamSyncRequest req : uniqueRequests.values()) {
            StreamSessionEntity session = sessionMap.get(req.streamId());
            if (session != null) {
                session.updatePeakViewers(req.concurrentUserCount());
                timelineEntities.add(new ViewMetricTimelineEntity(
                    req.streamId(),
                    session.getSessionId(),
                    currentTime,
                    req.concurrentUserCount()
                ));
            }
        }

        if (!timelineEntities.isEmpty()) {
            timelineRepository.saveAll(timelineEntities);
        }
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
