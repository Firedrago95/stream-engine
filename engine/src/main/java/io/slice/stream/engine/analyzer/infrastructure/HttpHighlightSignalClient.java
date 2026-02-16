package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.HighlightSignalClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class HttpHighlightSignalClient implements HighlightSignalClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiServerUrl;
    private final String engineSecret;

    public HttpHighlightSignalClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${api-server.uri}") String apiServerUrl,
            @Value("${api-server.secret}") String engineSecret
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiServerUrl = apiServerUrl;
        this.engineSecret = engineSecret;
    }

    @Override
    public void send(List<AnalysisSignal> signals) {
        try {
            String body = objectMapper.writeValueAsString(signals);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiServerUrl))
                .header("Content-Type", "application/json")
                .header("X-SL-ENGINE-TOKEN", engineSecret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMillis(1000))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        log.warn("신호 전송 실패 응답: {} (대상: {}건)", response.statusCode(), signals.size());
                    }
                })
                .exceptionally(e -> {
                    log.warn("네트워크 통신 에러 (API 서버 확인 필요): {}", e.getMessage());
                    return null;
                });

        } catch (Exception e) {
            log.error("신호 전송 준비 실패 (내부 로직 확인 필요): {}건", signals.size(), e);
        }
    }
}
