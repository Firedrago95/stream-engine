package io.slice.stream.apiserver.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SystemConfigServiceTest {

    private FakeSystemConfigRepository systemConfigRepository;
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigRepository = new FakeSystemConfigRepository();
        systemConfigService = new SystemConfigService(systemConfigRepository);
    }

    @Test
    void 설정값이_존재하지_않으면_기본값을_반환한다() {
        int limit = systemConfigService.getInt("targeting.limit", 300);
        String val = systemConfigService.get("unknown.key", "default_val");

        assertThat(limit).isEqualTo(300);
        assertThat(val).isEqualTo("default_val");
    }

    @Test
    void 정수형_설정값을_정상적으로_조회하고_캐싱한다() {
        systemConfigRepository.save(new SystemConfigEntity("targeting.limit", "350", "목표 인원", "TARGETING"));

        int limit = systemConfigService.getInt("targeting.limit", 300);
        assertThat(limit).isEqualTo(350);

        // 캐시 확인: DB 값을 임의로 날려도 캐시에서 350 반환
        systemConfigRepository.deleteAll();
        int cachedLimit = systemConfigService.getInt("targeting.limit", 300);
        assertThat(cachedLimit).isEqualTo(350);
    }

    @Test
    void 설정값_변경_시_DB와_캐시가_모두_갱신된다() {
        systemConfigRepository.save(new SystemConfigEntity("highlight.leading_buffer_seconds", "40", "버퍼", "HIGHLIGHT"));

        systemConfigService.updateConfig("highlight.leading_buffer_seconds", "45");

        int updated = systemConfigService.getInt("highlight.leading_buffer_seconds", 40);
        assertThat(updated).isEqualTo(45);

        SystemConfigEntity inDb = systemConfigRepository.findById("highlight.leading_buffer_seconds").orElseThrow();
        assertThat(inDb.getConfigValue()).isEqualTo("45");
    }

    @Test
    void 존재하지_않는_설정_키_변경_시_예외가_발생한다() {
        assertThatThrownBy(() -> systemConfigService.updateConfig("non.existent.key", "100"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 전체_설정_목록을_카테고리_순으로_조회한다() {
        systemConfigRepository.save(new SystemConfigEntity("targeting.limit", "300", "설명1", "TARGETING"));
        systemConfigRepository.save(new SystemConfigEntity("highlight.cooldown", "60", "설명2", "HIGHLIGHT"));

        List<SystemConfigDto> configs = systemConfigService.getAllConfigs();

        assertThat(configs).hasSize(2);
        assertThat(configs.get(0).category()).isEqualTo("HIGHLIGHT");
        assertThat(configs.get(1).category()).isEqualTo("TARGETING");
    }
}
