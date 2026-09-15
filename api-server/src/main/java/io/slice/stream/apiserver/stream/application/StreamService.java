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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamRepository streamRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaViewMetricTimelineRepository timelineRepository;

    @Transactional
    public void syncAll(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Instant currentTime = Instant.now();

        Map<String, StreamSyncRequest> uniqueRequests = requests.stream()
            .collect(Collectors.toMap(
                StreamSyncRequest::streamId,
                req -> req,
                (existing, replacement) -> replacement
            ));

        for (StreamSyncRequest req : uniqueRequests.values()) {
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

        List<String> streamIds = new ArrayList<>(uniqueRequests.keySet());
        List<StreamSessionEntity> activeSessions = sessionRepository.findAllActiveSessions(streamIds);
        Map<String, StreamSessionEntity> activeSessionMap = activeSessions.stream()
            .collect(Collectors.toMap(StreamSessionEntity::getStreamId, s -> s, (a, b) -> a));

        Map<String, StreamSessionEntity> sessionMap = new HashMap<>();
        List<StreamSessionEntity> newSessions = new ArrayList<>();
        List<StreamSessionSegmentEntity> newSegments = new ArrayList<>();

        for (StreamSyncRequest req : uniqueRequests.values()) {
            StreamSessionEntity activeSession = activeSessionMap.get(req.streamId());
            if (activeSession != null) {
                if (Objects.equals(activeSession.getSessionId(), req.liveId())) {
                    sessionMap.put(req.streamId(), activeSession);
                } else {
                    activeSession.finishSession(currentTime, null);
                    segmentRepository.findActiveSegment(activeSession.getSessionId())
                        .ifPresent(segment -> {
                            long endOffset = Duration.between(activeSession.getStartedAt(), currentTime).toMillis();
                            segment.endSegment(currentTime, endOffset);
                        });
                    log.info("[Sync] 이전 세션 종료 (새 방송 감지) - Stream: {}, OldSession: {}, NewLiveId: {}",
                        req.streamId(), activeSession.getSessionId(), req.liveId());
                }
            }
        }

        List<String> remainingLiveIds = uniqueRequests.values().stream()
            .filter(req -> !sessionMap.containsKey(req.streamId()))
            .map(StreamSyncRequest::liveId)
            .filter(Objects::nonNull)
            .toList();

        if (!remainingLiveIds.isEmpty()) {
            List<StreamSessionEntity> existingSessions = sessionRepository.findAllBySessionIdIn(remainingLiveIds);
            Map<String, StreamSessionEntity> existingSessionMap = existingSessions.stream()
                .collect(Collectors.toMap(StreamSessionEntity::getSessionId, s -> s, (a, b) -> a));

            List<StreamSessionSegmentEntity> activeSegments = segmentRepository.findAllActiveSegments(
                new ArrayList<>(existingSessionMap.keySet())
            );
            Set<String> activeSegmentSessionIds = activeSegments.stream()
                .map(StreamSessionSegmentEntity::getSessionId)
                .collect(Collectors.toSet());

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
        }

        for (StreamSyncRequest req : uniqueRequests.values()) {
            if (!sessionMap.containsKey(req.streamId())) {
                Instant sessionStartedAt = req.startedAt() != null ? req.startedAt() : currentTime;
                StreamSessionEntity session = new StreamSessionEntity(
                    req.streamId(),
                    req.liveId(),
                    req.liveTitle(),
                    req.categoryName(),
                    sessionStartedAt
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
        }
        if (!newSegments.isEmpty()) {
            segmentRepository.saveAll(newSegments);
            log.info("[Sync] 신규 방송 세션/세그먼트 벌크 생성 완료 - 세션 {}건, 세그먼트 {}건",
                newSessions.size(), newSegments.size());
        }

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

        log.info("[Sync] Native Upsert 완료 - {}건 (중복 제거 전: {}건, 시계열 적재: {}건)",
            uniqueRequests.size(), requests.size(), timelineEntities.size());
    }
}
