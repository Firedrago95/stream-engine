package io.slice.stream.apiserver.analysis.presentation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.apiserver.analysis.application.service.AnalysisCommandService;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisSignalRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${analysis.signal.path}")
public class AnalysisSignalController {

    private final AnalysisCommandService analysisCommandService;
    private final Counter signalsReceivedCounter;
    private final Counter signalBatchesCounter;

    public AnalysisSignalController(AnalysisCommandService analysisCommandService, MeterRegistry meterRegistry) {
        this.analysisCommandService = analysisCommandService;
        this.signalsReceivedCounter = Counter.builder("apiserver.signals.received")
            .description("엔진으로부터 수신된 화력 신호 누적 수")
            .register(meterRegistry);
        this.signalBatchesCounter = Counter.builder("apiserver.signal.batches.received")
            .description("엔진으로부터 수신된 신호 배치 요청 누적 수")
            .register(meterRegistry);
    }

    @PostMapping
    public ResponseEntity<Void> receive(
        @RequestBody @Valid List<AnalysisSignalRequest> requests
    ) {
        log.info("{}건의 방송 화력 정보 수신", requests.size());

        signalBatchesCounter.increment();
        signalsReceivedCounter.increment(requests.size());

        List<AnalysisSignal> signals = requests.stream()
            .map(AnalysisSignalRequest::toDomain)
            .toList();

        analysisCommandService.processSignals(signals);

        return ResponseEntity.accepted().build();
    }
}
