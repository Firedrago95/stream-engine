package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerEntity;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerRepository;
import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator.CalculationResult;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator.SimilarityMatch;
import io.slice.stream.apiserver.streamer.domain.similarity.StreamerChatterProvider;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerSimilarityRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamerSimilarityBatchService {

    private static final int WEEKS_TO_ANALYZE = 4;

    private final TargetStreamerRepository targetStreamerRepository;
    private final JpaStreamRepository streamRepository;
    private final StreamerChatterProvider streamerChatterProvider;
    private final StreamerSimilarityCalculator similarityCalculator;
    private final JpaStreamerSimilarityRepository similarityRepository;

    @Transactional
    public void executeBatch(LocalDate targetDate) {
        List<TargetStreamerEntity> activeTargets = targetStreamerRepository.findAllByIsActiveTrue();
        if (activeTargets.isEmpty()) {
            log.info("[Similarity-Batch] 활성화된 타겟 스트리머가 없어 배치를 건너뜁니다.");
            return;
        }

        List<String> streamIds = activeTargets.stream()
            .map(TargetStreamerEntity::getChannelId)
            .toList();

        Map<String, TargetStreamerEntity> targetMap = activeTargets.stream()
            .collect(Collectors.toMap(TargetStreamerEntity::getChannelId, target -> target, (a, b) -> a));

        Map<String, StreamEntity> streamMetaMap = loadStreamMetadata(streamIds);

        List<String> recentYearWeeks = calculateRecentYearWeeks(targetDate);
        Map<String, Set<Long>> allChatters = streamerChatterProvider.loadRecentChattersForStreamers(
            streamIds,
            recentYearWeeks
        );

        List<StreamerSimilarityEntity> entitiesToSave = new ArrayList<>();
        for (String streamId : streamIds) {
            CalculationResult result = similarityCalculator.calculate(streamId, allChatters);
            List<StreamerSimilarityEntity> entities = mapToEntities(
                streamId,
                result,
                targetDate,
                targetMap,
                streamMetaMap
            );
            entitiesToSave.addAll(entities);
        }

        similarityRepository.deleteByCalculatedDate(targetDate);
        if (!entitiesToSave.isEmpty()) {
            similarityRepository.saveAll(entitiesToSave);
        }

        log.info("[Similarity-Batch] 스트리머 시청자 유사도 배치 완료 - 일자: {}, 대상: {}명, 저장: {}건",
            targetDate, streamIds.size(), entitiesToSave.size());
    }

    private Map<String, StreamEntity> loadStreamMetadata(List<String> streamIds) {
        if (streamRepository == null) {
            return Collections.emptyMap();
        }
        try {
            List<StreamEntity> streams = streamRepository.findAllByStreamIdIn(streamIds);
            return streams.stream()
                .collect(Collectors.toMap(StreamEntity::getStreamId, stream -> stream, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[Similarity-Batch] 스트림 메타데이터 조회 중 오류 발생 (기본 타겟 정보로 대체합니다): {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<StreamerSimilarityEntity> mapToEntities(
        String streamId,
        CalculationResult result,
        LocalDate targetDate,
        Map<String, TargetStreamerEntity> targetMap,
        Map<String, StreamEntity> streamMetaMap
    ) {
        SimilarityStatus status = result.status();
        if (status != SimilarityStatus.NORMAL) {
            return List.of(StreamerSimilarityEntity.emptyState(streamId, status, targetDate));
        }

        List<StreamerSimilarityEntity> list = new ArrayList<>();
        int rank = 1;
        for (SimilarityMatch match : result.matches()) {
            String targetStreamId = match.targetStreamId();
            StreamEntity meta = streamMetaMap.get(targetStreamId);
            TargetStreamerEntity targetEntity = targetMap.get(targetStreamId);

            String targetName = resolveTargetName(meta, targetEntity, targetStreamId);
            String profileImage = (meta != null) ? meta.getProfileImageUrl() : null;
            String category = (meta != null) ? meta.getCategoryName() : null;

            list.add(new StreamerSimilarityEntity(
                streamId,
                SimilarityStatus.NORMAL,
                targetStreamId,
                targetName,
                profileImage,
                category,
                rank++,
                match.similarityPercent(),
                match.commonChatterCount(),
                match.totalChatterCount(),
                targetDate
            ));
        }
        return list;
    }

    private String resolveTargetName(
        StreamEntity meta,
        TargetStreamerEntity targetEntity,
        String defaultId
    ) {
        if (meta != null && meta.getStreamerName() != null && !meta.getStreamerName().isBlank()) {
            return meta.getStreamerName();
        }
        if (targetEntity != null && targetEntity.getStreamerName() != null && !targetEntity.getStreamerName().isBlank()) {
            return targetEntity.getStreamerName();
        }
        return defaultId;
    }

    public List<String> calculateRecentYearWeeks(LocalDate baseDate) {
        Set<String> weeks = new LinkedHashSet<>();
        for (int i = 0; i < WEEKS_TO_ANALYZE; i++) {
            LocalDate date = baseDate.minusWeeks(i);
            int year = date.get(WeekFields.ISO.weekBasedYear());
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            weeks.add(String.format("%d-W%02d", year, week));
        }
        return new ArrayList<>(weeks);
    }
}
