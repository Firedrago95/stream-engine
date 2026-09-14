package io.slice.stream.apiserver.streamer.presentation;

import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.streamer.application.StreamerGrassQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerProfileQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSessionQueryService;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
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
@RequestMapping("/api/v1/streamers")
@RequiredArgsConstructor
public class StreamerQueryController {

    private final StreamerLeaderboardQueryService leaderboardQueryService;
    private final StreamerProfileQueryService profileQueryService;
    private final StreamerGrassQueryService grassQueryService;
    private final StreamerSessionQueryService sessionQueryService;

    @GetMapping
    public ResponseEntity<List<StreamResponse>> getLeaderboard(
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(leaderboardQueryService.getLeaderboard(keyword));
    }

    @GetMapping("/{channelId}/profile")
    public ResponseEntity<StreamerProfileResponse> getProfile(
        @PathVariable String channelId
    ) {
        return ResponseEntity.ok(profileQueryService.getProfile(channelId));
    }

    @GetMapping("/{channelId}/grass")
    public ResponseEntity<StreamerGrassResponse> getGrass(
        @PathVariable String channelId,
        @RequestParam(required = false) Integer days
    ) {
        return ResponseEntity.ok(grassQueryService.getGrassData(channelId, days));
    }

    @GetMapping("/{channelId}/sessions")
    public ResponseEntity<StreamerSessionHistoryResponse> getSessions(
        @PathVariable String channelId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(sessionQueryService.getSessionHistory(channelId, page, size));
    }
}
