package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.service.HighlightQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.RecentHighlightSessionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class HighlightController {

    private final HighlightQueryService highlightQueryService;

    // 하이라이트 조회
    @GetMapping("/streams/{streamId}/highlights")
    public ResponseEntity<List<HighlightResponse>> getHighlights(
        @PathVariable String streamId,
        @RequestParam(name = "sessionId", required = false) String sessionId
    ) {
        return ResponseEntity.ok(highlightQueryService.getHighlightsBySessionId(streamId, sessionId));
    }

    // 최근 분석 완료된 세션 목록 조회
    @GetMapping("/highlights/recent-sessions")
    public ResponseEntity<List<RecentHighlightSessionResponse>> getRecentHighlightSessions(
        @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(highlightQueryService.getRecentFinishedSessions(limit));
    }
}
