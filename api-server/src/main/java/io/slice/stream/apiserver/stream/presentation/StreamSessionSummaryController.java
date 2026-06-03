package io.slice.stream.apiserver.stream.presentation;

import io.slice.stream.apiserver.stream.application.StreamSessionService;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSessionSummaryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${analysis.summary.path}")
@RequiredArgsConstructor
public class StreamSessionSummaryController {

    private final StreamSessionService streamSessionService;

    @PostMapping("/{streamId}")
    public ResponseEntity<Void> summarize (
        @PathVariable("streamId") String streamId,
        @RequestBody StreamSessionSummaryRequest summaries
    ) {
        log.info("[Summary] 방송 요약 정보 수신 streamId={}", streamId);
        streamSessionService.updateSessionSummary(streamId, summaries);
        return ResponseEntity.ok().build();
    }
}
