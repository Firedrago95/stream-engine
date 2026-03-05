package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.service.AnalysisQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisQueryController {

    private final AnalysisQueryService analysisQueryService;

    // 분석 데이터 요청 API
    @GetMapping("/{streamId}")
    public ResponseEntity<AnalysisResponse> getAnalysis(
        @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String streamId) {
        log.info("[Query] 분석 데이터 조회 요청 - 스트림: {}", streamId);

        return ResponseEntity.ok(analysisQueryService.getRecentAnalysis(streamId));
    }

    // 가용 날짜 목록 API
    @GetMapping("/streams/{streamId}/available-dates")
    public ResponseEntity<List<String>> getAvailableDates(
        @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String streamId,
        @RequestParam(name = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
        @RequestParam(name = "limit", defaultValue = "10") int limit) {

        log.info("[Query] 가용 날짜 목록 조회 - 스트림: {}, 커서: {}, 개수: {}", streamId, before, limit);
        return ResponseEntity.ok(analysisQueryService.getAvailableDates(streamId, before, limit));
    }

    // 과거 분석 데이터 차트 API
    @GetMapping("/streams/{streamId}/history")
    public ResponseEntity<AnalysisResponse> getHistoryAnalysis(
        @PathVariable @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String streamId,
        @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("[Query] 과거 데이터 차트 조회 - 스트림: {}, 날짜: {}", streamId, date);
        return ResponseEntity.ok(analysisQueryService.getHistoryAnalysis(streamId, date));
    }
}
