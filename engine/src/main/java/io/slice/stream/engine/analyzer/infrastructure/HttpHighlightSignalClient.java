package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.signal.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.signal.HighlightSignalClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class HttpHighlightSignalClient implements HighlightSignalClient {

    private final RestClient restClient;
    private final String apiPath;

    public HttpHighlightSignalClient(
        @Qualifier("apiServerRestClient") RestClient restClient,
        @Value("${api-server.path}") String apiPath
    ) {
        this.restClient = restClient;
        this.apiPath = apiPath;
    }

    @Override
    public void send(List<AnalysisSignal> signals) {
        if (signals.isEmpty()) return;

        try {
            restClient.post()
                .uri(apiPath)
                .body(signals)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.warn("[Signal Error] 신호 전송 실패: {} (대상: {}건)",
                        response.getStatusCode(), signals.size());
                })
                .toBodilessEntity();

            log.debug("[Signal] 분석 신호 {}건 전송 완료", signals.size());

        } catch (Exception e) {
            log.error("[Signal Failed] 네트워크 통신 에러 (대상: {}건): {}",
                signals.size(), e.getMessage());
        }
    }
}
