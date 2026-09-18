package io.slice.stream.apiserver.streamer.presentation;

import io.slice.stream.apiserver.streamer.application.StreamerFollowerCommandService;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${analysis.internal-prefix}")
@RequiredArgsConstructor
public class StreamerFollowerInternalController {

    private final StreamerFollowerCommandService followerCommandService;

    @GetMapping("/follower-targets")
    public ResponseEntity<List<String>> getFollowerTargets() {
        log.info("[Internal API] 엔진으로부터 일일 팔로워 수집 대상 채널 목록 요청 수신");
        List<String> targets = followerCommandService.findFollowerTargetChannelIds();
        log.info("[Internal API] 팔로워 수집 대상 채널 목록 반환 완료 (총 {}건)", targets.size());
        return ResponseEntity.ok(targets);
    }

    @PostMapping("/follower-snapshots")
    public ResponseEntity<Void> recordFollowerSnapshots(
        @RequestBody List<FollowerSnapshotRecordDto> snapshotRecords
    ) {
        log.info("[Internal API] 엔진으로부터 일일 팔로워 스냅샷 데이터 수신 (총 {}건)",
            snapshotRecords != null ? snapshotRecords.size() : 0);
        followerCommandService.recordFollowers(snapshotRecords);
        return ResponseEntity.ok().build();
    }
}
