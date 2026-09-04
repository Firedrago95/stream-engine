package io.slice.stream.engine.analyzer.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final Counter signalsSentSuccessCounter;
    private final Counter signalsSentErrorCounter;
    private final Timer signalsSendTimer;

    public HttpHighlightSignalClient(
        @Qualifier("apiServerRestClient") RestClient restClient,
        @Value("${api-server.signal-path}") String apiPath,
        MeterRegistry meterRegistry
    ) {
        this.restClient = restClient;
        this.apiPath = apiPath;
        this.signalsSentSuccessCounter = Counter.builder("engine.signals.sent")
            .tag("status", "success")
            .description("OCI API 서버로 전송 성공한 화력 신호 수")
            .register(meterRegistry);
        this.signalsSentErrorCounter = Counter.builder("engine.signals.sent")
            .tag("status", "error")
            .description("OCI API 서버로 전송 실패한 화력 신호 수")
            .register(meterRegistry);
        this.signalsSendTimer = Timer.builder("engine.signals.send.duration")
            .description("OCI API 서버 신호 전송 레이턴시")
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
    }

    @Override
    public void send(List<AnalysisSignal> signals) {
        if (signals.isEmpty()) return;

        try {
            signalsSendTimer.record(() -> {
                restClient.post()
                    .uri(apiPath)
                    .body(signals)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        signalsSentErrorCounter.increment(signals.size());
                        log.warn("[Signal Error] 신호 전송 실패: {} (대상: {}건)",
                            response.getStatusCode(), signals.size());
                    })
                    .toBodilessEntity();
            });

            signalsSentSuccessCounter.increment(signals.size());
            log.debug("[Signal] 분석 신호 {}건 전송 완료", signals.size());

        } catch (Exception e) {
            signalsSentErrorCounter.increment(signals.size());
            log.error("[Signal Failed] 네트워크 통신 에러 (대상: {}건): {}",
                signals.size(), e.getMessage());
        }
    }
}
