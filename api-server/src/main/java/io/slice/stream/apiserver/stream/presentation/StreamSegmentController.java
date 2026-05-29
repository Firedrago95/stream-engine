package io.slice.stream.apiserver.stream.presentation;

import io.slice.stream.apiserver.stream.application.StreamSessionService;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
@RequestMapping("${analysis.meta.path}")
@RequiredArgsConstructor
public class StreamSegmentController {

    private final StreamSessionService streamSessionService;

    @PostMapping
    public ResponseEntity<Void> recordSegments(
        @RequestBody @NotEmpty @Valid List<ChangedStreamRequest> requests
    ) {
        log.info("[Segment-API] {}건의 메타데이터 변경 세그먼트 요청 수신", requests.size());
        streamSessionService.updateSessionSegment(requests);
        return ResponseEntity.accepted().build();
    }
}
