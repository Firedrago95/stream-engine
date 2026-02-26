package io.slice.stream.apiserver.stream.presentation;

import io.slice.stream.apiserver.stream.application.StreamService;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.List;
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
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamSyncController {

    private final StreamService streamService;

    @PostMapping("/{slug}")
    public ResponseEntity<Void> sync (
        @PathVariable String slug,
        @RequestBody List<StreamSyncRequest> streams
    )
    {
      log.info("[Sync] {}개 방송 수신 (Slug: {})", streams.size(), slug);
      streamService.syncAll(streams);
      return ResponseEntity.ok().build();
    }
}
