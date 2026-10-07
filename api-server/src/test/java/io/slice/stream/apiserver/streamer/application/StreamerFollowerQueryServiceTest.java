package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingResponse;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingType;
import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerGrowthProjection;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamerFollowerQueryServiceTest {

    private FakeStreamerFollowerSnapshotRepository snapshotRepository;
    private FakeStreamRepository streamRepository;
    private StreamerFollowerQueryService queryService;

    @BeforeEach
    void setUp() {
        snapshotRepository = new FakeStreamerFollowerSnapshotRepository();
        streamRepository = new FakeStreamRepository();
        queryService = new StreamerFollowerQueryService(snapshotRepository, streamRepository);
    }

    @Test
    @DisplayName("지정된 기간 동안의 팔로워 트렌드 목록을 날짜 오름차순으로 반환한다")
    void getFollowerTrend() {
        String streamId = "ch1";
        int days = 30;
        LocalDate now = LocalDate.now();

        StreamerFollowerSnapshotEntity snapshot1 =
            new StreamerFollowerSnapshotEntity(streamId, now.minusDays(2), 10000, 50);
        StreamerFollowerSnapshotEntity snapshot2 =
            new StreamerFollowerSnapshotEntity(streamId, now.minusDays(1), 10100, 100);
        StreamerFollowerSnapshotEntity snapshot3 =
            new StreamerFollowerSnapshotEntity(streamId, now, 10150, 50);

        snapshotRepository.addSnapshot(snapshot1);
        snapshotRepository.addSnapshot(snapshot2);
        snapshotRepository.addSnapshot(snapshot3);

        List<FollowerTrendResponse> result = queryService.getFollowerTrend(streamId, days);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(now.minusDays(2));
        assertThat(result.get(0).followerCount()).isEqualTo(10000);
        assertThat(result.get(0).followerGrowth()).isEqualTo(50);

        assertThat(result.get(1).followerCount()).isEqualTo(10100);
        assertThat(result.get(1).followerGrowth()).isEqualTo(100);

        assertThat(result.get(2).followerCount()).isEqualTo(10150);
        assertThat(result.get(2).followerGrowth()).isEqualTo(50);
    }

    @Test
    @DisplayName("기본 기간은 30일이며 최대 90일을 초과하지 않는다")
    void getFollowerTrendWithMaxDaysCap() {
        String streamId = "ch1";
        LocalDate now = LocalDate.now();

        StreamerFollowerSnapshotEntity oldSnapshot =
            new StreamerFollowerSnapshotEntity(streamId, now.minusDays(95), 5000, 10);
        snapshotRepository.addSnapshot(oldSnapshot);

        List<FollowerTrendResponse> result = queryService.getFollowerTrend(streamId, 365);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("중간에 수집되지 않은 날짜가 있으면 직전 팔로워 수로 보간(Forward Fill)하고 증감량은 0으로 채운다")
    void getFollowerTrendWithForwardFill() {
        String streamId = "ch1";
        LocalDate day1 = LocalDate.now().minusDays(3);
        LocalDate day4 = LocalDate.now();

        StreamerFollowerSnapshotEntity snapshot1 =
            new StreamerFollowerSnapshotEntity(streamId, day1, 10000, 50);
        StreamerFollowerSnapshotEntity snapshot4 =
            new StreamerFollowerSnapshotEntity(streamId, day4, 10200, 0);

        snapshotRepository.addSnapshot(snapshot1);
        snapshotRepository.addSnapshot(snapshot4);

        List<FollowerTrendResponse> result = queryService.getFollowerTrend(streamId, 30);

        assertThat(result).hasSize(4);

        assertThat(result.get(0).date()).isEqualTo(day1);
        assertThat(result.get(0).followerCount()).isEqualTo(10000);
        assertThat(result.get(0).followerGrowth()).isEqualTo(50);

        assertThat(result.get(1).date()).isEqualTo(day1.plusDays(1));
        assertThat(result.get(1).followerCount()).isEqualTo(10000);
        assertThat(result.get(1).followerGrowth()).isEqualTo(0);

        assertThat(result.get(2).date()).isEqualTo(day1.plusDays(2));
        assertThat(result.get(2).followerCount()).isEqualTo(10000);
        assertThat(result.get(2).followerGrowth()).isEqualTo(0);

        assertThat(result.get(3).date()).isEqualTo(day4);
        assertThat(result.get(3).followerCount()).isEqualTo(10200);
        assertThat(result.get(3).followerGrowth()).isEqualTo(0);
    }

    @Test
    @DisplayName("누적 팔로워 순위 조회 시 팔로워 수가 많은 순으로 내림차순 정렬하여 반환한다")
    void getFollowerLeaderboard_totalFollowers() {
        StreamEntity s1 = new StreamEntity("ch1", "스트리머1");
        s1.updateChannelMetrics(50000, 100);
        s1.heartbeat("스트리머1", "방송중", "https://img1.png", "종합게임", 2000);

        StreamEntity s2 = new StreamEntity("ch2", "스트리머2");
        s2.updateChannelMetrics(100000, 500);
        s2.markOffline();

        StreamEntity s3 = new StreamEntity("ch3", "스트리머3");
        s3.updateChannelMetrics(10000, 50);

        streamRepository.addStreams(List.of(s1, s2, s3));

        List<FollowerRankingResponse> result =
            queryService.getFollowerLeaderboard(FollowerRankingType.TOTAL, 20);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).streamId()).isEqualTo("ch2");
        assertThat(result.get(0).followerCount()).isEqualTo(100000);
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.OFFLINE);

        assertThat(result.get(1).streamId()).isEqualTo("ch1");
        assertThat(result.get(1).followerCount()).isEqualTo(50000);
        assertThat(result.get(1).status()).isEqualTo(StreamStatus.LIVE);

        assertThat(result.get(2).streamId()).isEqualTo("ch3");
        assertThat(result.get(2).followerCount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("주간 팔로워 급상승 조회 시 주간 증가량이 많은 순으로 반환한다")
    void getFollowerLeaderboard_weeklyGrowth() {
        TestGrowthProjection p1 = new TestGrowthProjection(
            "ch_rising1", "라이징1", "화제의 방송", "https://img1.png", "토크", true, 1500, 20000, 4820
        );
        TestGrowthProjection p2 = new TestGrowthProjection(
            "ch_rising2", "라이징2", "게임 방송", "https://img2.png", "롤", false, 0, 50000, 2150
        );

        streamRepository.setFollowerGrowthProjections(List.of(p1, p2));

        List<FollowerRankingResponse> result =
            queryService.getFollowerLeaderboard(FollowerRankingType.GROWTH, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).streamId()).isEqualTo("ch_rising1");
        assertThat(result.get(0).weeklyGrowth()).isEqualTo(4820);
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.LIVE);

        assertThat(result.get(1).streamId()).isEqualTo("ch_rising2");
        assertThat(result.get(1).weeklyGrowth()).isEqualTo(2150);
        assertThat(result.get(1).status()).isEqualTo(StreamStatus.OFFLINE);
    }

    private record TestGrowthProjection(
        String streamId,
        String streamerName,
        String liveTitle,
        String profileImageUrl,
        String categoryName,
        boolean isLive,
        int concurrentUserCount,
        int followerCount,
        int weeklyGrowth
    ) implements StreamerFollowerGrowthProjection {
        @Override public String getStreamId() { return streamId; }
        @Override public String getStreamerName() { return streamerName; }
        @Override public String getLiveTitle() { return liveTitle; }
        @Override public String getProfileImageUrl() { return profileImageUrl; }
        @Override public String getCategoryName() { return categoryName; }
        @Override public boolean getIsLive() { return isLive; }
        @Override public int getConcurrentUserCount() { return concurrentUserCount; }
        @Override public int getFollowerCount() { return followerCount; }
        @Override public int getWeeklyGrowth() { return weeklyGrowth; }
    }
}
