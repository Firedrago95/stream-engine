package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.FollowerSnapshotRecord;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.ChzzkChannelClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyFollowerCollectorScheduler {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 2000L;

    private final ApiServerClient apiServerClient;
    private final ChzzkChannelClient chzzkChannelClient;
    private final ExecutorService virtualThreadExecutor;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final int chunkSize;

    @Autowired
    public DailyFollowerCollectorScheduler(
        ApiServerClient apiServerClient,
        ChzzkChannelClient chzzkChannelClient,
        ExecutorService virtualThreadExecutor
    ) {
        this(apiServerClient, chzzkChannelClient, virtualThreadExecutor, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF_MS, DEFAULT_CHUNK_SIZE);
    }

    public DailyFollowerCollectorScheduler(
        ApiServerClient apiServerClient,
        ChzzkChannelClient chzzkChannelClient,
        ExecutorService virtualThreadExecutor,
        int maxRetries,
        long initialBackoffMs
    ) {
        this(apiServerClient, chzzkChannelClient, virtualThreadExecutor, maxRetries, initialBackoffMs, DEFAULT_CHUNK_SIZE);
    }

    public DailyFollowerCollectorScheduler(
        ApiServerClient apiServerClient,
        ChzzkChannelClient chzzkChannelClient,
        ExecutorService virtualThreadExecutor,
        int maxRetries,
        long initialBackoffMs,
        int chunkSize
    ) {
        this.apiServerClient = apiServerClient;
        this.chzzkChannelClient = chzzkChannelClient;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.chunkSize = chunkSize;
    }

    @Scheduled(cron = "${chzzk.collector.follower.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void collectDailyFollowers() {
        log.info("[Follower Collector] 일일 팔로워 정기 수집 작업을 시작합니다.");

        List<String> targetChannels = fetchTargetChannelsWithRetry();
        if (targetChannels.isEmpty()) {
            log.warn("[Follower Collector] 수집 대상 채널 목록이 비어 있어 작업을 건너뜁니다.");
            return;
        }

        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        log.info("[Follower Collector] 총 {}개 채널에 대해 D-1({}) 마감 스냅샷 수집을 진행합니다.",
            targetChannels.size(), snapshotDate);

        List<FollowerSnapshotRecord> collectedRecords = collectSnapshots(targetChannels, snapshotDate);

        if (collectedRecords.isEmpty()) {
            log.warn("[Follower Collector] 수집된 팔로워 데이터가 없습니다.");
            return;
        }

        sendSnapshotsInChunks(collectedRecords);
        log.info("[Follower Collector] 일일 팔로워 수집 및 전송 완료 (총 {}건)", collectedRecords.size());
    }

    private List<String> fetchTargetChannelsWithRetry() {
        int attempt = 0;
        long backoff = initialBackoffMs;
        while (attempt < maxRetries) {
            attempt++;
            try {
                Optional<List<String>> targetChannelsOpt = apiServerClient.fetchFollowerTargetChannels();
                if (targetChannelsOpt.isPresent()) {
                    return targetChannelsOpt.get();
                }
                log.warn("[Follower Collector] 대상 채널 목록 응답이 없습니다. 재시도합니다. (시도: {}/{})", attempt, maxRetries);
            } catch (Exception e) {
                log.warn("[Follower Collector] 대상 채널 조회 중 오류 발생 (시도: {}/{}): {}", attempt, maxRetries, e.getMessage());
            }

            if (attempt < maxRetries) {
                sleep(backoff);
                backoff *= 2;
            }
        }
        log.error("[Follower Collector] 대상 채널 조회 {}회 재시도 모두 실패", maxRetries);
        return Collections.emptyList();
    }

    private void sendSnapshotsInChunks(List<FollowerSnapshotRecord> records) {
        List<List<FollowerSnapshotRecord>> chunks = partitionRecords(records, chunkSize);
        int totalChunks = chunks.size();

        for (int i = 0; i < totalChunks; i++) {
            List<FollowerSnapshotRecord> chunk = chunks.get(i);
            int chunkIndex = i + 1;
            sendSingleChunkWithRetry(chunk, chunkIndex, totalChunks);
        }
    }

    private List<List<FollowerSnapshotRecord>> partitionRecords(List<FollowerSnapshotRecord> records, int size) {
        List<List<FollowerSnapshotRecord>> partitions = new ArrayList<>();
        for (int i = 0; i < records.size(); i += size) {
            int end = Math.min(i + size, records.size());
            partitions.add(records.subList(i, end));
        }
        return partitions;
    }

    private void sendSingleChunkWithRetry(List<FollowerSnapshotRecord> chunk, int chunkIndex, int totalChunks) {
        int attempt = 0;
        long backoff = initialBackoffMs;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                apiServerClient.sendFollowerSnapshots(chunk);
                log.info("[Follower Collector] 팔로워 스냅샷 청크 전송 완료 ({}/{} 청크, {}건)",
                    chunkIndex, totalChunks, chunk.size());
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("[Follower Collector] 팔로워 스냅샷 청크 전송 실패 (청크: {}/{}, 시도: {}/{}): {}",
                    chunkIndex, totalChunks, attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    sleep(backoff);
                    backoff *= 2;
                }
            }
        }

        log.error("[Follower Collector] 팔로워 스냅샷 청크 전송 최종 실패 (청크: {}/{}, {}건)",
            chunkIndex, totalChunks, chunk.size(), lastException);
        throw new IllegalStateException(
            String.format("[Follower Collector] 팔로워 스냅샷 청크(%d/%d) 전송이 %d회 재시도 후에도 최종 실패했습니다.",
                chunkIndex, totalChunks, maxRetries),
            lastException
        );
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[Follower Collector] 재시도 대기 중 인터럽트 발생", e);
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
