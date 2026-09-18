package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import io.slice.stream.apiserver.streamer.application.dto.StreamFollowerUpdateDto;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.StreamerFollowerJdbcRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
public class StreamerFollowerCommandService {

    private static final int DAYS_90 = 90;

    private final StreamerFollowerSnapshotRepository snapshotRepository;
    private final JpaStreamRepository streamRepository;
    private final JpaStreamSessionRepository streamSessionRepository;
    private final StreamerFollowerJdbcRepository followerJdbcRepository;

    @Transactional
    public void recordFollower(String streamId, int followerCount, LocalDate snapshotDate) {
        recordFollowers(List.of(new FollowerSnapshotRecordDto(streamId, followerCount, snapshotDate)));
    }

    @Transactional
    public void recordFollowers(List<FollowerSnapshotRecordDto> snapshotRecords) {
        if (snapshotRecords == null || snapshotRecords.isEmpty()) {
            return;
        }

        Map<LocalDate, List<FollowerSnapshotRecordDto>> recordsByDate = snapshotRecords.stream()
            .collect(Collectors.groupingBy(FollowerSnapshotRecordDto::snapshotDate));

        Instant now = Instant.now();
        for (Map.Entry<LocalDate, List<FollowerSnapshotRecordDto>> entry : recordsByDate.entrySet()) {
            processFollowersByDate(entry.getKey(), entry.getValue(), now);
        }

        log.info("[Follower Sync] 일일 팔로워 스냅샷 벌크 적재 완료 (총 {}건)", snapshotRecords.size());
    }

    private void processFollowersByDate(
        LocalDate snapshotDate,
        List<FollowerSnapshotRecordDto> records,
        Instant now
    ) {
        LocalDate yesterday = snapshotDate.minusDays(1);
        List<String> streamIds = records.stream()
            .map(FollowerSnapshotRecordDto::channelId)
            .toList();

        Map<String, Integer> prevFollowerMap = fetchPreviousFollowerMap(yesterday, streamIds);

        List<StreamerFollowerSnapshotEntity> snapshotsToUpsert = new ArrayList<>(records.size());
        List<StreamFollowerUpdateDto> streamUpdates = new ArrayList<>(records.size());

        for (FollowerSnapshotRecordDto record : records) {
            int growth = calculateGrowth(record, prevFollowerMap);
            snapshotsToUpsert.add(createSnapshotEntity(record, growth));
            streamUpdates.add(new StreamFollowerUpdateDto(record.channelId(), record.followerCount(), now));
        }

        followerJdbcRepository.batchUpsertSnapshots(snapshotsToUpsert);
        followerJdbcRepository.batchUpdateStreamFollowers(streamUpdates);
    }

    private Map<String, Integer> fetchPreviousFollowerMap(LocalDate yesterday, List<String> streamIds) {
        List<StreamerFollowerSnapshotEntity> prevSnapshots =
            snapshotRepository.findAllBySnapshotDateAndStreamIdIn(yesterday, streamIds);

        return prevSnapshots.stream()
            .collect(Collectors.toMap(
                StreamerFollowerSnapshotEntity::getStreamId,
                StreamerFollowerSnapshotEntity::getFollowerCount,
                (existing, replacement) -> existing
            ));
    }

    private int calculateGrowth(
        FollowerSnapshotRecordDto record,
        Map<String, Integer> prevFollowerMap
    ) {
        Integer prevCount = prevFollowerMap.get(record.channelId());
        if (prevCount == null) {
            return 0;
        }
        return record.followerCount() - prevCount;
    }

    private StreamerFollowerSnapshotEntity createSnapshotEntity(
        FollowerSnapshotRecordDto record,
        int growth
    ) {
        return new StreamerFollowerSnapshotEntity(
            record.channelId(),
            record.snapshotDate(),
            record.followerCount(),
            growth
        );
    }

    @Transactional(readOnly = true)
    public List<String> findFollowerTargetChannelIds() {
        Instant ninetyDaysAgo = Instant.now().minus(DAYS_90, ChronoUnit.DAYS);
        List<String> targetStreamIds = streamSessionRepository.findDistinctStreamIdsByStartedAtAfter(ninetyDaysAgo);
        log.info("[Follower Target] 최근 90일 이내 활동한 팔로워 수집 대상 채널 조회 완료 (총 {}건)", targetStreamIds.size());
        return targetStreamIds;
    }
}
