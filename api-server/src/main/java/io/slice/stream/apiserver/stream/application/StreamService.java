package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                (oldReq, newReq) -> newReq
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
        Map<String, StreamSessionEntity> sessionMap = activeSessions.stream()
            .collect(Collectors.toMap(StreamSessionEntity::getStreamId, s -> s, (a, b) -> a));

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
