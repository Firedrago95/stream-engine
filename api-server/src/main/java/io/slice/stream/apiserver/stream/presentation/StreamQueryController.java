package io.slice.stream.apiserver.stream.presentation;

import io.slice.stream.apiserver.stream.application.StreamQueryService;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamQueryController {

    private final StreamQueryService streamQueryService;

    @GetMapping
    public ResponseEntity<List<StreamResponse>> getStreams(
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(streamQueryService.getBrowserList(keyword));
    }
}
