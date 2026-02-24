package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.AnalysisQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisQueryController {

    private final AnalysisQueryService analysisQueryService;

    @GetMapping("/{streamId}")
    public ResponseEntity<AnalysisResponse> getAnalysis(
        @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String streamId) {
        log.info("[Query] 분석 데이터 조회 요청 - 스트림: {}", streamId);

        return ResponseEntity.ok(analysisQueryService.getRecentAnalysis(streamId));
    }
}
