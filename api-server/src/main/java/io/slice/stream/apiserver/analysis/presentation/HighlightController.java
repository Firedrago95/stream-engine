package io.slice.stream.apiserver.analysis.presentation;

import io.slice.stream.apiserver.analysis.application.service.HighlightQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LOGICAL_DAY_OFFSET = 6;

    private final HighlightQueryService highlightQueryService;

    @GetMapping("/streams/{streamId}/highlights")
    public ResponseEntity<List<HighlightResponse>> getHighlights(
        @PathVariable String streamId,
        @RequestParam(name = "date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
        ) {
        LocalDate targetDate = date != null ? date
            : ZonedDateTime.now(KST).minusHours(LOGICAL_DAY_OFFSET).toLocalDate();

        return ResponseEntity.ok(highlightQueryService.getHighlightsByDate(streamId, targetDate));
    }
}
