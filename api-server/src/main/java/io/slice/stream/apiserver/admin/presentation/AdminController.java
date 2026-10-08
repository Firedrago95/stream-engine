package io.slice.stream.apiserver.admin.presentation;

import io.slice.stream.apiserver.admin.auth.AdminAuthService;
import io.slice.stream.apiserver.admin.config.SystemConfigDto;
import io.slice.stream.apiserver.admin.config.SystemConfigService;
import io.slice.stream.apiserver.admin.metrics.AdminMetricsOverviewDto;
import io.slice.stream.apiserver.admin.metrics.AdminMetricsService;
import io.slice.stream.apiserver.admin.presentation.dto.AdminLoginRequest;
import io.slice.stream.apiserver.admin.presentation.dto.AdminLoginResponse;
import io.slice.stream.apiserver.admin.presentation.dto.ExcludeChannelRequest;
import io.slice.stream.apiserver.admin.presentation.dto.ExcludedChannelResponse;
import io.slice.stream.apiserver.admin.presentation.dto.UpdateConfigRequest;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final TargetStreamerService targetStreamerService;
    private final SystemConfigService systemConfigService;
    private final AdminMetricsService adminMetricsService;
    private final StreamerLeaderboardQueryService streamerLeaderboardQueryService;

    @PostMapping("/auth/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        String token = adminAuthService.login(request.password());
        return ResponseEntity.ok(new AdminLoginResponse(token, 86400L));
    }

    @GetMapping("/auth/check")
    public ResponseEntity<Void> checkAuth() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/metrics/overview")
    public ResponseEntity<AdminMetricsOverviewDto> getMetricsOverview() {
        return ResponseEntity.ok(adminMetricsService.getOverview());
    }

    @GetMapping("/targets/excluded")
    public ResponseEntity<List<ExcludedChannelResponse>> getExcludedTargets() {
        List<ExcludedChannelResponse> responses = targetStreamerService.getEffectiveExcludedTargets().stream()
            .map(ExcludedChannelResponse::from)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/targets/exclude")
    @CacheEvict(value = "targetChannels", allEntries = true)
    public ResponseEntity<Void> excludeChannel(@RequestBody ExcludeChannelRequest request) {
        Instant expiresAt = null;
        if (request.durationDays() != null && request.durationDays() > 0) {
            expiresAt = Instant.now().plus(request.durationDays(), ChronoUnit.DAYS);
        }
        targetStreamerService.excludeStreamer(
            request.channelId(),
            request.streamerName(),
            request.reason(),
            expiresAt
        );
        streamerLeaderboardQueryService.refreshDailyLeaderboard();
        log.info("[Admin API] 채널 격리(제외) 및 리더보드 갱신 완료: channelId={}", request.channelId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/targets/exclude/{channelId}")
    @CacheEvict(value = "targetChannels", allEntries = true)
    public ResponseEntity<Void> restoreChannel(@PathVariable("channelId") String channelId) {
        targetStreamerService.restoreStreamer(channelId);
        streamerLeaderboardQueryService.refreshDailyLeaderboard();
        log.info("[Admin API] 채널 격리(제외) 해제 및 리더보드 갱신 완료: channelId={}", channelId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/targets/cache-refresh")
    @CacheEvict(value = "targetChannels", allEntries = true)
    public ResponseEntity<Void> refreshTargetCache() {
        streamerLeaderboardQueryService.refreshDailyLeaderboard();
        log.info("[Admin API] 타겟 채널 및 리더보드 캐시 수동 초기화 완료");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/configs")
    public ResponseEntity<List<SystemConfigDto>> getConfigs() {
        return ResponseEntity.ok(systemConfigService.getAllConfigs());
    }

    @PutMapping("/configs/{key}")
    public ResponseEntity<Void> updateConfig(
        @PathVariable("key") String key,
        @RequestBody UpdateConfigRequest request
    ) {
        systemConfigService.updateConfig(key, request.value());
        log.info("[Admin API] 시스템 파라미터 수정 완료: key={}, value={}", key, request.value());
        return ResponseEntity.ok().build();
    }
}
