package io.slice.stream.apiserver.stream.presentation;

import io.slice.stream.apiserver.stream.application.StreamService;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
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
@RequestMapping("${analysis.sync.path}")
@RequiredArgsConstructor
public class StreamSyncController {

    private final StreamService streamService;

    @PostMapping
    public ResponseEntity<Void> sync (
        @RequestBody @NotEmpty @Valid List<StreamSyncRequest> streams
    ) {
      log.info("[Sync] {}개 방송 수신", streams.size());
      streamService.syncAll(streams);
      return ResponseEntity.ok().build();
    }
}
