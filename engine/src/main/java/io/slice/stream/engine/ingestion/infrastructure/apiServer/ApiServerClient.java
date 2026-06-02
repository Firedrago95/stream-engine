package io.slice.stream.engine.ingestion.infrastructure.apiServer;

import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ApiServerClient {

    private final RestClient restClient;
    private final String syncPath;
    private final String metaPath;
    private final String summaryPath;

    public ApiServerClient(
        @Qualifier("apiServerRestClient") RestClient restClient,
        @Value("${api-server.sync-path}") String syncPath,
        @Value("${api-server.meta-path}") String metaPath,
        @Value("${api-server.summary-path}") String summaryPath
    ) {
        this.restClient = restClient;
        this.syncPath = syncPath;
        this.metaPath = metaPath;
        this.summaryPath = summaryPath;
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
    public void sendSessionSummaryAsync(String streamId, double subscriberChatRatio) {
        try {
            restClient.post()
                .uri(summaryPath, streamId)
                .body(Map.of("subscriberChatRatio", subscriberChatRatio))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[Summary Error] 서버 응답 오류: {}", response.getStatusCode());
                })
                .toBodilessEntity();

            log.info("[Summary] 방송 종료 요약 전송 완료 streamId ={}", streamId);
        } catch (Exception e) {
            log.error("[Summary Failed] 방송 종료 요약 정보 전송 API 서버 통신 중 에러: {}", e.getMessage());
        }
    }
}
