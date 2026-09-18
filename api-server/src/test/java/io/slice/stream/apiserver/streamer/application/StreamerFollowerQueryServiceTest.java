package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamerFollowerQueryServiceTest {

    @Mock
    private StreamerFollowerSnapshotRepository snapshotRepository;

    private StreamerFollowerQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new StreamerFollowerQueryService(snapshotRepository);
    }

    @Test
    @DisplayName("지정된 기간 동안의 팔로워 트렌드 목록을 날짜 오름차순으로 반환한다")
    void getFollowerTrend() {
        String streamId = "ch1";
        int days = 30;
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusDays(days);

        StreamerFollowerSnapshotEntity snapshot1 =
            new StreamerFollowerSnapshotEntity(streamId, now.minusDays(2), 10000, 50);
        StreamerFollowerSnapshotEntity snapshot2 =
            new StreamerFollowerSnapshotEntity(streamId, now.minusDays(1), 10100, 100);
        StreamerFollowerSnapshotEntity snapshot3 =
            new StreamerFollowerSnapshotEntity(streamId, now, 10150, 50);

        when(snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(streamId, startDate))
            .thenReturn(List.of(snapshot1, snapshot2, snapshot3));

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
        LocalDate cappedStartDate = now.minusDays(90);

        when(snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(streamId, cappedStartDate))
            .thenReturn(List.of());

        List<FollowerTrendResponse> result = queryService.getFollowerTrend(streamId, 365);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("중간에 수집되지 않은 날짜가 있으면 직전 팔로워 수로 보간(Forward Fill)하고 증감량은 0으로 채운다")
    void getFollowerTrendWithForwardFill() {
        String streamId = "ch1";
        LocalDate day1 = LocalDate.of(2026, 9, 10);
        LocalDate day4 = LocalDate.of(2026, 9, 13);

        StreamerFollowerSnapshotEntity snapshot1 =
            new StreamerFollowerSnapshotEntity(streamId, day1, 10000, 50);
        StreamerFollowerSnapshotEntity snapshot4 =
            new StreamerFollowerSnapshotEntity(streamId, day4, 10200, 0);

        when(snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(any(), any()))
            .thenReturn(List.of(snapshot1, snapshot4));

        List<FollowerTrendResponse> result = queryService.getFollowerTrend(streamId, 30);

        assertThat(result).hasSize(4);

        assertThat(result.get(0).date()).isEqualTo(day1);
        assertThat(result.get(0).followerCount()).isEqualTo(10000);
        assertThat(result.get(0).followerGrowth()).isEqualTo(50);

        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(result.get(1).followerCount()).isEqualTo(10000);
        assertThat(result.get(1).followerGrowth()).isEqualTo(0);

        assertThat(result.get(2).date()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(result.get(2).followerCount()).isEqualTo(10000);
        assertThat(result.get(2).followerGrowth()).isEqualTo(0);

        assertThat(result.get(3).date()).isEqualTo(day4);
        assertThat(result.get(3).followerCount()).isEqualTo(10200);
        assertThat(result.get(3).followerGrowth()).isEqualTo(0);
    }
}
