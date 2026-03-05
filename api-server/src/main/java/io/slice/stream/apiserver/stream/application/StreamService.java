package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
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

        Map<String, StreamEntity> existingStreamMap = getExistingStreams(requests);

        List<StreamEntity> streamsToSave = prepareStreamsToSave(requests, existingStreamMap);

        streamRepository.saveAll(streamsToSave);
        log.info("[Sync] DB 동기화 완료 - 현재 라이브 하트비트 갱신: {}건", streamsToSave.size());
    }

    private Map<String, StreamEntity> getExistingStreams(List<StreamSyncRequest> requests) {
        List<String> streamIds = requests.stream()
            .map(StreamSyncRequest::streamId)
            .toList();

        return streamRepository.findAllByStreamIdIn(streamIds).stream()
            .collect(Collectors.toMap(StreamEntity::getStreamId, entity -> entity));
    }

    private List<StreamEntity> prepareStreamsToSave(
        List<StreamSyncRequest> requests,
        Map<String, StreamEntity> existingMap
    ) {
        return requests.stream()
            .map(req -> createOrUpdateStream(req, existingMap))
            .toList();
    }

    private StreamEntity createOrUpdateStream(
        StreamSyncRequest req,
        Map<String, StreamEntity> existingMap
    ) {
        StreamEntity entity = existingMap.getOrDefault(
            req.streamId(),
            new StreamEntity(req.streamId(), req.streamerName())
        );

        entity.heartbeat(
            req.streamerName(),
            req.liveTitle(),
            req.profileImageUrl(),
            req.categoryName()
        );

        return entity;
    }
}
