package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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

    @Transactional
    public void recordFollower(String streamId, int followerCount, LocalDate snapshotDate) {
        updateStreamMaster(streamId, followerCount);
        upsertSnapshot(streamId, followerCount, snapshotDate);
    }

    @Transactional
    public void recordFollowers(List<FollowerSnapshotRecordDto> snapshotRecords) {
        if (snapshotRecords == null || snapshotRecords.isEmpty()) {
            return;
        }
        for (FollowerSnapshotRecordDto record : snapshotRecords) {
            try {
                recordFollower(record.channelId(), record.followerCount(), record.snapshotDate());
            } catch (Exception e) {
                log.warn("[Follower Sync] 채널 팔로워 스냅샷 적재 실패. streamId: {}, 사유: {}", record.channelId(), e.getMessage());
            }
        }
        log.info("[Follower Sync] 일일 팔로워 스냅샷 적재 완료 (총 {}건)", snapshotRecords.size());
    }

    @Transactional(readOnly = true)
    public List<String> findFollowerTargetChannelIds() {
        Instant ninetyDaysAgo = Instant.now().minus(DAYS_90, ChronoUnit.DAYS);
        List<String> targetStreamIds = streamSessionRepository.findDistinctStreamIdsByStartedAtAfter(ninetyDaysAgo);
        log.info("[Follower Target] 최근 90일 이내 활동한 팔로워 수집 대상 채널 조회 완료 (총 {}건)", targetStreamIds.size());
        return targetStreamIds;
    }

    private void updateStreamMaster(String streamId, int followerCount) {
        Optional<StreamEntity> streamOpt = streamRepository.findByStreamId(streamId);
        if (streamOpt.isPresent()) {
            StreamEntity stream = streamOpt.get();
            stream.updateFollower(followerCount, Instant.now());
        }
    }

    private void upsertSnapshot(String streamId, int followerCount, LocalDate snapshotDate) {
        int growth = calculateGrowth(streamId, followerCount, snapshotDate);

        Optional<StreamerFollowerSnapshotEntity> existingOpt =
            snapshotRepository.findByStreamIdAndSnapshotDate(streamId, snapshotDate);

        if (existingOpt.isPresent()) {
            StreamerFollowerSnapshotEntity existing = existingOpt.get();
            existing.updateMetrics(followerCount, growth);
        } else {
            StreamerFollowerSnapshotEntity newSnapshot = new StreamerFollowerSnapshotEntity(
                streamId,
                snapshotDate,
                followerCount,
                growth
            );
            snapshotRepository.save(newSnapshot);
        }
    }

    private int calculateGrowth(String streamId, int currentFollowerCount, LocalDate snapshotDate) {
        Optional<StreamerFollowerSnapshotEntity> prevOpt =
            snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, snapshotDate);

        if (prevOpt.isEmpty()) {
            return 0;
        }

        StreamerFollowerSnapshotEntity prev = prevOpt.get();
        if (!prev.getSnapshotDate().equals(snapshotDate.minusDays(1))) {
            return 0;
        }

        return currentFollowerCount - prev.getFollowerCount();
    }
}
