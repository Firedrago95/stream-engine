package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
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

        log.info("[Sync] Native Upsert 완료 - {}건 (중복 제거 전: {}건)", uniqueRequests.size(), requests.size());
    }
}
