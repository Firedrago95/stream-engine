package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.service.AnalysisQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 가용 세션 목록 API
    @GetMapping("/streams/{streamId}/available-sessions")
    public ResponseEntity<List<SessionResponse>> getAvailableSessions(@PathVariable String streamId, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analysisQueryService.getAvailableSessions(streamId, limit));
    }

    // 과거 분석 데이터 차트 API
    @GetMapping("/streams/{streamId}/history")
    public ResponseEntity<AnalysisResponse> getHistoryAnalysis(@PathVariable String streamId, @RequestParam(name = "sessionId") String sessionId) {
        return ResponseEntity.ok(analysisQueryService.getHistoryAnalysis(streamId, sessionId));
    }
}
