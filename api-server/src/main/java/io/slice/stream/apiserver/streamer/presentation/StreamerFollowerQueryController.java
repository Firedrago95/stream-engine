package io.slice.stream.apiserver.streamer.presentation;

import io.slice.stream.apiserver.streamer.application.StreamerFollowerQueryService;
import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
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
public class StreamerFollowerQueryController {

    private final StreamerFollowerQueryService followerQueryService;

    @GetMapping("/{channelId}/follower-trend")
    public ResponseEntity<List<FollowerTrendResponse>> getFollowerTrend(
        @PathVariable String channelId,
        @RequestParam(defaultValue = "30") int days
    ) {
        log.info("[Streamer Follower] 팔로워 추이 조회 요청. channelId: {}, days: {}", channelId, days);
        List<FollowerTrendResponse> trend = followerQueryService.getFollowerTrend(channelId, days);
        return ResponseEntity.ok(trend);
    }
}
