package io.slice.stream.apiserver.streamer.presentation.dto;

import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record StreamerSimilarityResponse(
    String streamId,
    SimilarityStatus status,
    LocalDate calculatedDate,
    List<SimilarChannelDto> items
) {

    public static StreamerSimilarityResponse empty(String streamId) {
        return new StreamerSimilarityResponse(
            streamId,
            SimilarityStatus.INSUFFICIENT_DATA,
            null,
            Collections.emptyList()
        );
    }

    public static StreamerSimilarityResponse from(String streamId, List<StreamerSimilarityEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return empty(streamId);
        }

        StreamerSimilarityEntity first = entities.get(0);
        SimilarityStatus status = first.getStatus();
        LocalDate calculatedDate = first.getCalculatedDate();

        if (status != SimilarityStatus.NORMAL) {
            return new StreamerSimilarityResponse(streamId, status, calculatedDate, Collections.emptyList());
        }

        List<SimilarChannelDto> items = entities.stream()
            .filter(e -> e.getTargetStreamId() != null)
            .map(e -> new SimilarChannelDto(
                e.getRankOrder(),
                e.getTargetStreamId(),
                e.getTargetStreamerName(),
                e.getTargetProfileImage(),
                e.getTargetPrimaryCategory(),
                e.getSimilarityPercent(),
                e.getCommonChatterCount(),
                e.getTotalChatterCount()
            ))
            .toList();

        return new StreamerSimilarityResponse(streamId, status, calculatedDate, items);
    }

    public record SimilarChannelDto(
        int rank,
        String channelId,
        String streamerName,
        String profileImageUrl,
        String primaryCategory,
        Double similarityPercent,
        Integer commonChatterCount,
        Integer totalChatterCount
    ) {}
}
