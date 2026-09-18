package io.slice.stream.engine.ingestion.infrastructure.apiServer;

import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.FollowerSnapshotRecord;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSessionSummary;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ApiServerClient {

    private final RestClient restClient;
    private final String syncPath;
    private final String metaPath;
    private final String summaryPath;
    private final String targetsPath;
    private final String followerTargetsPath;
    private final String followerSnapshotsPath;

    @Autowired
    public ApiServerClient(
        @Qualifier("apiServerRestClient") RestClient restClient,
        @Value("${api-server.sync-path}") String syncPath,
        @Value("${api-server.meta-path}") String metaPath,
        @Value("${api-server.summary-path}") String summaryPath,
        @Value("${api-server.targets-path}") String targetsPath,
        @Value("${api-server.follower-targets-path:/api/v1/internal/follower-targets}") String followerTargetsPath,
        @Value("${api-server.follower-snapshots-path:/api/v1/internal/follower-snapshots}") String followerSnapshotsPath
    ) {
        this.restClient = restClient;
        this.syncPath = syncPath;
        this.metaPath = metaPath;
        this.summaryPath = summaryPath;
        this.targetsPath = targetsPath;
        this.followerTargetsPath = followerTargetsPath;
        this.followerSnapshotsPath = followerSnapshotsPath;
    }

    public ApiServerClient(
        RestClient restClient,
        String syncPath,
        String metaPath,
        String summaryPath,
        String targetsPath
    ) {
        this(restClient, syncPath, metaPath, summaryPath, targetsPath,
            "/api/v1/internal/follower-targets", "/api/v1/internal/follower-snapshots");
    }

    @Retryable(
        includes = RestClientException.class,
        maxRetries = 3,
        delay = 1000
    )
    public Optional<List<String>> fetchTargetChannels() {
        try {
            List<String> channels = restClient.get()
                .uri(targetsPath)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});

            return Optional.of(channels != null ? channels : Collections.emptyList());
        } catch (Exception e) {
            log.warn("[Targets Pull] API 서버로부터 타겟 명단 조회 실패 (기존 로컬 캐시 유지): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Async
    public void syncStreams(List<StreamSyncRequest> requests) {
        if (requests.isEmpty()) return;

        try {
            restClient.post()
                .uri(syncPath)
                .body(requests)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[Sync Error] 서버 응답 오류: {}", response.getStatusCode());
                })
                .toBodilessEntity();

            log.info("[Sync] {}개의 방송 목록 동기화 완료", requests.size());
        } catch (Exception e) {
            log.error("[Sync Failed] 방송 목록 동기화 API 서버 통신 중 에러: {}", e.getMessage());
        }
    }

    @Async
    public void recordNewSegments(List<ChangedStream> requests) {
        if (requests.isEmpty()) return;

        try {
            restClient.post()
                .uri(metaPath)
                .body(requests)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[Segment Sync Error] 서버 응답 오류: {}", response.getStatusCode());
                })
                .toBodilessEntity();

            log.info("[Segment Sync] {}개의 세그먼트 구간 동기화 완료", requests.size());
        } catch (Exception e) {
            log.error("[Sync Failed] 방송 메타데이터 변경 API 서버 통신 중 에러: {}", e.getMessage());
        }
    }

    @Async
    public void sendSessionSummaryAsync(StreamSessionSummary summary) {
        try {
            restClient.post()
                .uri(summaryPath, summary.streamId())
                .body(summary)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[Summary Error] 서버 응답 오류: {}", response.getStatusCode());
                })
                .toBodilessEntity();

            log.info("[Summary] 방송 종료 요약 전송 완료 streamId ={}", summary.streamId());
        } catch (Exception e) {
            log.error("[Summary Failed] 방송 종료 요약 정보 전송 API 서버 통신 중 에러: {}", e.getMessage());
        }
    }

    @Retryable(
        includes = RestClientException.class,
        maxRetries = 3,
        delay = 1000
    )
    public Optional<List<String>> fetchFollowerTargetChannels() {
        try {
            List<String> channels = restClient.get()
                .uri(followerTargetsPath)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});

            return Optional.of(channels != null ? channels : Collections.emptyList());
        } catch (Exception e) {
            log.warn("[Follower Targets Pull] API 서버로부터 팔로워 대상 명단 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void sendFollowerSnapshots(List<FollowerSnapshotRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                .uri(followerSnapshotsPath)
                .body(records)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[Follower Sync Error] 서버 응답 오류: {}", response.getStatusCode());
                })
                .toBodilessEntity();

            log.info("[Follower Sync] {}개의 팔로워 스냅샷 전송 완료", records.size());
        } catch (Exception e) {
            log.error("[Follower Sync Failed] 일일 팔로워 스냅샷 API 서버 전송 실패: {}", e.getMessage());
        }
    }
}
