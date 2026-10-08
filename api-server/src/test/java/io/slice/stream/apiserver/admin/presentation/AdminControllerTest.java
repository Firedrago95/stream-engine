package io.slice.stream.apiserver.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.apiserver.admin.auth.AdminAuthService;
import io.slice.stream.apiserver.admin.config.FakeSystemConfigRepository;
import io.slice.stream.apiserver.admin.config.SystemConfigDto;
import io.slice.stream.apiserver.admin.config.SystemConfigEntity;
import io.slice.stream.apiserver.admin.config.SystemConfigService;
import io.slice.stream.apiserver.admin.metrics.AdminMetricsOverviewDto;
import io.slice.stream.apiserver.admin.metrics.AdminMetricsService;
import io.slice.stream.apiserver.admin.presentation.dto.AdminLoginRequest;
import io.slice.stream.apiserver.admin.presentation.dto.AdminLoginResponse;
import io.slice.stream.apiserver.admin.presentation.dto.ExcludeChannelRequest;
import io.slice.stream.apiserver.admin.presentation.dto.ExcludedChannelResponse;
import io.slice.stream.apiserver.admin.presentation.dto.UpdateConfigRequest;
import io.slice.stream.apiserver.stream.fake.FakeAnalysisRepository;
import io.slice.stream.apiserver.stream.fake.FakeHighlightEventRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import io.slice.stream.apiserver.stream.fake.FakeTargetStreamerRepository;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AdminControllerTest {

    private static final String SECRET_PASSWORD = "test-secret-key-1234";

    private AdminAuthService adminAuthService;
    private FakeTargetStreamerRepository targetStreamerRepository;
    private TargetStreamerService targetStreamerService;
    private FakeSystemConfigRepository systemConfigRepository;
    private SystemConfigService systemConfigService;
    private AdminMetricsService adminMetricsService;
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(SECRET_PASSWORD);
        targetStreamerRepository = new FakeTargetStreamerRepository();
        FakeStreamRepository streamRepository = new FakeStreamRepository();
        systemConfigRepository = new FakeSystemConfigRepository();
        systemConfigService = new SystemConfigService(systemConfigRepository);
        targetStreamerService = new TargetStreamerService(targetStreamerRepository, streamRepository, systemConfigService);

        FakeStringRedisTemplate redisTemplate = new FakeStringRedisTemplate();
        FakeHighlightEventRepository highlightRepository = new FakeHighlightEventRepository();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        adminMetricsService = new AdminMetricsService(redisTemplate, streamRepository, highlightRepository, meterRegistry);

        FakeAnalysisRepository analysisRepository = new FakeAnalysisRepository();
        JsonMapper jsonMapper = JsonMapper.builder().build();
        StreamerLeaderboardQueryService leaderboardQueryService = new StreamerLeaderboardQueryService(
            streamRepository,
            analysisRepository,
            targetStreamerService,
            redisTemplate,
            jsonMapper
        );

        adminController = new AdminController(
            adminAuthService,
            targetStreamerService,
            systemConfigService,
            adminMetricsService,
            leaderboardQueryService
        );
    }

    @Test
    void 비밀번호가_일치하면_토큰이_발급된다() {
        ResponseEntity<AdminLoginResponse> response = adminController.login(new AdminLoginRequest(SECRET_PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(adminAuthService.validateToken(response.getBody().token())).isTrue();
    }

    @Test
    void 잘못된_비밀번호_입력_시_예외가_발생한다() {
        assertThatThrownBy(() -> adminController.login(new AdminLoginRequest("wrong-password")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 채널_격리_등록_및_해제가_정상_동작한다() {
        ExcludeChannelRequest request = new ExcludeChannelRequest(
            "ch_asian_mbc",
            "아시안게임 MBC",
            "대회 종료",
            30
        );

        ResponseEntity<Void> excludeResponse = adminController.excludeChannel(request);
        assertThat(excludeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<ExcludedChannelResponse>> listResponse = adminController.getExcludedTargets();
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody().get(0).channelId()).isEqualTo("ch_asian_mbc");
        assertThat(listResponse.getBody().get(0).reason()).isEqualTo("대회 종료");

        ResponseEntity<Void> restoreResponse = adminController.restoreChannel("ch_asian_mbc");
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<ExcludedChannelResponse>> afterRestoreList = adminController.getExcludedTargets();
        assertThat(afterRestoreList.getBody()).isEmpty();
    }

    @Test
    void 시스템_설정_조회_및_수정이_정상_동작한다() {
        systemConfigRepository.save(new SystemConfigEntity("highlight.leading_buffer_seconds", "40", "버퍼", "HIGHLIGHT"));

        ResponseEntity<Void> updateResponse = adminController.updateConfig(
            "highlight.leading_buffer_seconds",
            new UpdateConfigRequest("50")
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<SystemConfigDto>> listResponse = adminController.getConfigs();
        assertThat(listResponse.getBody()).isNotEmpty();
        SystemConfigDto config = listResponse.getBody().stream()
            .filter(c -> "highlight.leading_buffer_seconds".equals(c.configKey()))
            .findFirst()
            .orElseThrow();
        assertThat(config.configValue()).isEqualTo("50");
    }
}
