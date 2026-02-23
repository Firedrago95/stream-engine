package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.AnalysisService;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisSignalRequest;
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
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody List<AnalysisSignalRequest> requests) {
        log.info("{}건의 방송 화력 정보 수신", requests.size());

        List<AnalysisSignal> signals = requests.stream()
            .map(AnalysisSignalRequest::toDomain)
            .toList();

        analysisService.processSignals(signals);

        return ResponseEntity.accepted().build();
    }
}
