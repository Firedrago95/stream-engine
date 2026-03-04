package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.service.AnalysisCommandService;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisSignalRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${analysis.signal.path}")
@RequiredArgsConstructor
public class AnalysisSignalController {

    private final AnalysisCommandService analysisCommandService;

    @PostMapping
    public ResponseEntity<Void> receive(
        @RequestBody @Valid List<AnalysisSignalRequest> requests
    ) {
        log.info("{}건의 방송 화력 정보 수신", requests.size());

        List<AnalysisSignal> signals = requests.stream()
            .map(AnalysisSignalRequest::toDomain)
            .toList();

        analysisCommandService.processSignals(signals);

        return ResponseEntity.accepted().build();
    }
}
