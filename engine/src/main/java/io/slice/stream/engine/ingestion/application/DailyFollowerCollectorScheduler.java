package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.FollowerSnapshotRecord;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.ChzzkChannelClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyFollowerCollectorScheduler {

    private final ApiServerClient apiServerClient;
    private final ChzzkChannelClient chzzkChannelClient;
    private final ExecutorService virtualThreadExecutor;

    @Scheduled(cron = "${chzzk.collector.follower.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void collectDailyFollowers() {
        log.info("[Follower Collector] 일일 팔로워 정기 수집 작업을 시작합니다.");

        Optional<List<String>> targetChannelsOpt = apiServerClient.fetchFollowerTargetChannels();
        if (targetChannelsOpt.isEmpty() || targetChannelsOpt.get().isEmpty()) {
            log.warn("[Follower Collector] 수집 대상 채널 목록이 비어 있어 작업을 건너뜁니다.");
            return;
        }

        List<String> targetChannels = targetChannelsOpt.get();
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        log.info("[Follower Collector] 총 {}개 채널에 대해 D-1({}) 마감 스냅샷 수집을 진행합니다.",
            targetChannels.size(), snapshotDate);

        List<FollowerSnapshotRecord> collectedRecords = collectSnapshots(targetChannels, snapshotDate);

        if (!collectedRecords.isEmpty()) {
            apiServerClient.sendFollowerSnapshots(collectedRecords);
            log.info("[Follower Collector] 일일 팔로워 수집 완료 (성공: {}/{}건)",
                collectedRecords.size(), targetChannels.size());
        } else {
            log.warn("[Follower Collector] 수집된 팔로워 데이터가 없습니다.");
        }
    }

    private List<FollowerSnapshotRecord> collectSnapshots(List<String> targetChannels, LocalDate snapshotDate) {
        List<CompletableFuture<FollowerSnapshotRecord>> futures = targetChannels.stream()
            .map(channelId -> CompletableFuture.supplyAsync(() -> fetchSingleSnapshot(channelId, snapshotDate), virtualThreadExecutor))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    private FollowerSnapshotRecord fetchSingleSnapshot(String channelId, LocalDate snapshotDate) {
        try {
            Optional<Integer> followerCountOpt = chzzkChannelClient.fetchFollowerCount(channelId);
            return followerCountOpt
                .map(count -> new FollowerSnapshotRecord(channelId, count, snapshotDate))
                .orElse(null);
        } catch (Exception e) {
            log.warn("[Follower Collector] 채널 팔로워 조회 실패. channelId: {}, 사유: {}", channelId, e.getMessage());
            return null;
        }
    }
}
