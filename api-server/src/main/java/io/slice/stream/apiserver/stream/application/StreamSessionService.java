package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSessionService {

    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamRepository streamRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = "activeSessions", key = "#streamId", sync = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String getOrCreateActiveSession(String streamId, Instant signalTime) {
        return sessionRepository.findActiveSession(streamId)
            .map(StreamSessionEntity::getSessionId)
            .orElseGet(() -> createNewSession(streamId, signalTime));
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
            .collect(Collectors.toMap(StreamSessionSegmentEntity::getSessionId, s -> s));

        List<StreamSessionSegmentEntity> segmentsToSave = new ArrayList<>();
        for (ChangedStreamRequest req : requests) {
            StreamSessionEntity session = sessionMap.get(req.streamId());
            if (session == null) continue;

            StreamSessionSegmentEntity activeSegment = segmentMap.get(session.getSessionId());
            processSegmentUpdate(req, session, activeSegment)
                .ifPresent(segmentsToSave::add);
        }

        if (!segmentsToSave.isEmpty()) {
            segmentRepository.saveAll(segmentsToSave);
        }
    }

    private java.util.Optional<StreamSessionSegmentEntity> processSegmentUpdate(
        ChangedStreamRequest req,
        StreamSessionEntity session,
        StreamSessionSegmentEntity activeSegment
    ) {
        if (Objects.equals(req.newCategory(), session.getCategoryName()) &&
            Objects.equals(req.newTitle(), session.getTitle())) {
            return java.util.Optional.empty();
        }

        if (activeSegment != null) {
            activeSegment.endSegment(req.changedAt(), req.changeOffsetMs());
        }

        session.updateMetadata(req.newTitle(), req.newCategory());

        return java.util.Optional.of(new StreamSessionSegmentEntity(
            req.streamId(),
            session.getSessionId(),
            req.newTitle(),
            req.newCategory(),
            req.changedAt(),
            req.changeOffsetMs()
        ));
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void closeOfflineSessions() {
        Instant offlineThreshold = Instant.now().minus(Duration.ofMinutes(6));
        List<StreamSessionEntity> sessionsToClose = sessionRepository.findSessionsToClose(offlineThreshold);

        for (StreamSessionEntity session : sessionsToClose) {
            session.finishSession(Instant.now(), null);

            segmentRepository.findActiveSegment(session.getSessionId())
                    .ifPresent(segment -> {
                        Instant endedAt = Instant.now();
                        long endOffset = Duration.between(session.getStartedAt() , endedAt).toMillis();
                        segment.endSegment(endedAt, endOffset);
                    });

            Objects.requireNonNull(cacheManager.getCache("activeSessions")).evict(session.getStreamId());
            log.info("[Session-Manager] 방송 종료 감지, 세션 마감 - Stream: {}, SessionId: {}", session.getStreamId(), session.getSessionId());
        }
    }

    private String createNewSession(String streamId, Instant startedAt) {
        String newSessionId = UUID.randomUUID().toString();
        StreamEntity streamInfo = streamRepository.findByStreamId(streamId).orElse(null);
        String title = (streamInfo != null) ? streamInfo.getLiveTitle() : "제목 없음";
        String category = (streamInfo != null) ? streamInfo.getCategoryName() : "카테고리 없음";

        StreamSessionEntity newSession = new StreamSessionEntity(streamId, newSessionId, title, category, startedAt);
        sessionRepository.save(newSession);

        StreamSessionSegmentEntity initialSegment =
            new StreamSessionSegmentEntity(streamId, newSessionId, title, category, startedAt, 0L);
        segmentRepository.save(initialSegment);

        log.info("[Session-Manager] 새로운 방송 세션 생성 - Stream: {}, SessionId: {}", streamId, newSessionId);
        return newSessionId;
    }

    @Transactional
    public void updateSessionSummary(String streamId, StreamSessionSummaryRequest summaries) {
        StreamSessionEntity session = sessionRepository.findActiveSession(streamId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_NOT_FOUND, "해당 방송의 세션을 찾을 수 없습니다."));

        session.updateSubscriberChatRatio(summaries.subscriberChatRatio());
    }
}
