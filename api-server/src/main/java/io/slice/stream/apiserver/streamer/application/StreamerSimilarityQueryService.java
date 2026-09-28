package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerSimilarityRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSimilarityResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerSimilarityQueryService {

    private final JpaStreamRepository streamRepository;
    private final JpaStreamerSimilarityRepository similarityRepository;

    @Cacheable(cacheNames = "streamerSimilarities", key = "#channelId")
    public StreamerSimilarityResponse getSimilarities(String channelId) {
        validateStreamExists(channelId);

        List<StreamerSimilarityEntity> entities = similarityRepository.findLatestSimilaritiesByStreamId(channelId);
        log.debug("스트리머 유사 채널 조회 완료: channelId={}, 건수={}", channelId, entities.size());

        return StreamerSimilarityResponse.from(channelId, entities);
    }

    private void validateStreamExists(String channelId) {
        streamRepository.findByStreamId(channelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_NOT_FOUND, "존재하지 않는 스트리머 채널입니다: " + channelId));
    }
}
