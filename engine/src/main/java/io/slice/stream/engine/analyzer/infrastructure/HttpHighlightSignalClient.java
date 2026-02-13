package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.HighlightSignalClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpHighlightSignalClient implements HighlightSignalClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${api-server.uri}")
    private String apiServerUrl;

    @Value("${api-server.secret}")
    private String engineSecret;

    @Override
    public void send(List<AnalysisSignal> signals) {
        try {
            String body = objectMapper.writeValueAsString(signals);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiServerUrl))
                .header("Content-Type", "application/json")
                .header("X-SL-ENGINE-TOKEN", engineSecret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
             log.error("배치 신호 전송 실패 (개수: {})", signals.size(), e);
        }
    }
}
