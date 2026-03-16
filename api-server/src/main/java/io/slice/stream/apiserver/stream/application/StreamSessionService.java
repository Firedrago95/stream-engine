package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.analysis.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
    private final CacheManager cacheManager;

    @Cacheable(value = "activeSessions", key = "#streamId", sync = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String getOrCreateActiveSession(String streamId, Instant signalTime) {
        return sessionRepository.findActiveSession(streamId)
            .map(StreamSessionEntity::getSessionId)
            .orElseGet(() -> createNewSession(streamId, signalTime));
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void closeOfflineSessions() {
        Instant offlineThreshold = Instant.now().minus(Duration.ofMinutes(3));
        List<StreamSessionEntity> sessionsToClose = sessionRepository.findSessionsToClose(offlineThreshold);

        for (StreamSessionEntity session : sessionsToClose) {
            session.finishSession(Instant.now(), null);
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

        log.info("[Session-Manager] 새로운 방송 세션 생성 - Stream: {}, SessionId: {}", streamId, newSessionId);
        return newSessionId;
    }

}
