package io.slice.stream.apiserver.stream.targeting;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.admin.config.FakeSystemConfigRepository;
import io.slice.stream.apiserver.admin.config.SystemConfigEntity;
import io.slice.stream.apiserver.admin.config.SystemConfigService;
import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeTargetStreamerRepository;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetStreamerServiceTest {

    private FakeTargetStreamerRepository targetStreamerRepository;
    private FakeStreamRepository streamRepository;
    private FakeSystemConfigRepository systemConfigRepository;
    private SystemConfigService systemConfigService;
    private TargetStreamerService targetStreamerService;

    @BeforeEach
    void setUp() {
        targetStreamerRepository = new FakeTargetStreamerRepository();
        streamRepository = new FakeStreamRepository();
        systemConfigRepository = new FakeSystemConfigRepository();
        systemConfigService = new SystemConfigService(systemConfigRepository);
        targetStreamerService = new TargetStreamerService(targetStreamerRepository, streamRepository, systemConfigService);
    }

    @Test
    void 활성_수동_공식_채널과_검증된_정규_활동_스트리머로_300명이_채워진_경우_실시간_보충을_호출하지_않는다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        List<StreamerLeaderboardProjection> verifiedStreamers = new ArrayList<>();
        for (int i = 1; i <= 299; i++) {
            verifiedStreamers.add(createProjection("ch_trend_" + i));
        }
        streamRepository.setTopStreamersWith30dAvg(verifiedStreamers);
        streamRepository.setTopStreamIdsByConcurrentUserCount(List.of("ch_should_not_be_included"));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).hasSize(300);
        assertThat(results).contains("ch_official", "ch_trend_1", "ch_trend_299");
        assertThat(results).doesNotContain("ch_should_not_be_included");
    }

    @Test
    void 정규_활동_스트리머가_300명_미만일_경우_300명이_될_때까지_실시간_시청자수_순으로_보충한다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        StreamerLeaderboardProjection p1 = createProjection("ch_trend1");
        StreamerLeaderboardProjection p2 = createProjection("ch_trend2");
        streamRepository.setTopStreamersWith30dAvg(List.of(p1, p2));
        streamRepository.setTopStreamIdsByConcurrentUserCount(List.of("ch_realtime1", "ch_trend1", "ch_realtime2"));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).containsExactly("ch_official", "ch_trend1", "ch_trend2", "ch_realtime1", "ch_realtime2");
    }

    @Test
    void 정규_활동_스트리머_데이터가_없을_경우_실시간_시청자수_기반으로_fallback_동작한다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        streamRepository.setTopStreamersWith30dAvg(Collections.emptyList());
        streamRepository.setTopStreamIdsByConcurrentUserCount(List.of("ch_fallback1", "ch_fallback2"));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).containsExactly("ch_official", "ch_fallback1", "ch_fallback2");
    }

    @Test
    void 제외_등록된_스트리머는_정규_활동_스트리머에_포함되어도_타겟_목록에서_제외된다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        // 아시안게임 채널을 30일 격리 등록
        targetStreamerService.excludeStreamer("ch_asian_games", "아시안게임 MBC", "대회 종료", Instant.now().plus(30, ChronoUnit.DAYS));

        StreamerLeaderboardProjection p1 = createProjection("ch_asian_games");
        StreamerLeaderboardProjection p2 = createProjection("ch_normal");
        streamRepository.setTopStreamersWith30dAvg(List.of(p1, p2));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).contains("ch_official", "ch_normal");
        assertThat(results).doesNotContain("ch_asian_games");
    }

    @Test
    void 임시_격리_만료일이_지난_제외_스트리머는_다시_타겟_목록에_포함된다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        // 이미 만료된 채널 (어제 만료)
        targetStreamerService.excludeStreamer("ch_expired_event", "과거 이벤트", "종료", Instant.now().minus(1, ChronoUnit.DAYS));

        StreamerLeaderboardProjection p1 = createProjection("ch_expired_event");
        streamRepository.setTopStreamersWith30dAvg(List.of(p1));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).contains("ch_official", "ch_expired_event");
    }

    @Test
    void 제외된_STATIC_공식_채널을_복구하면_원본_타입과_함께_타겟_목록으로_복귀한다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        targetStreamerRepository.save(official);

        // 1. 제외 처리
        targetStreamerService.excludeStreamer("ch_official", "공식 채널", "임시 제외", Instant.now().plus(10, ChronoUnit.DAYS));
        assertThat(targetStreamerService.getActiveTargetChannelIds()).doesNotContain("ch_official");

        // 2. 복구 처리
        targetStreamerService.restoreStreamer("ch_official");

        // 엔티티 원본 타입 복원 및 사유/만료일 초기화 검증
        TargetStreamerEntity restored = targetStreamerRepository.findByChannelId("ch_official").orElseThrow();
        assertThat(restored.getTargetType()).isEqualTo(TargetType.STATIC);
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.getPreviousTargetType()).isNull();
        assertThat(restored.getReason()).isNull();
        assertThat(restored.getExpiresAt()).isNull();

        // 타겟 목록 재조회 시 포함 확인
        assertThat(targetStreamerService.getActiveTargetChannelIds()).contains("ch_official");
    }

    @Test
    void 동적_시스템_설정으로_타겟_인원수가_변경되면_해당_제한에_맞춰_타겟을_수집한다() {
        systemConfigRepository.save(new SystemConfigEntity("targeting.limit", "5", "목표 인원", "TARGETING"));

        List<StreamerLeaderboardProjection> verifiedStreamers = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            verifiedStreamers.add(createProjection("ch_top_" + i));
        }
        streamRepository.setTopStreamersWith30dAvg(verifiedStreamers);
        streamRepository.setTopStreamIdsByConcurrentUserCount(List.of("ch_overflow_1", "ch_overflow_2"));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).hasSize(5);
        assertThat(results).containsExactly("ch_top_1", "ch_top_2", "ch_top_3", "ch_top_4", "ch_top_5");
    }

    private StreamerLeaderboardProjection createProjection(String streamId) {
        return new StreamerLeaderboardProjection() {
            @Override public String getStreamId() { return streamId; }
            @Override public String getStreamerName() { return "스트리머_" + streamId; }
            @Override public String getLiveTitle() { return "방송 방제"; }
            @Override public String getProfileImageUrl() { return "https://img.png"; }
            @Override public String getCategoryName() { return "종합게임"; }
            @Override public boolean getIsLive() { return true; }
            @Override public Instant getLastUpdateAt() { return Instant.now(); }
            @Override public int getConcurrentUserCount() { return 1000; }
            @Override public int getAverageViewers() { return 1000; }
        };
    }
}
