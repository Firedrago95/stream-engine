package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.FollowerSnapshotRecord;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.ChzzkChannelClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyFollowerCollectorSchedulerTest {

    @Mock
    private ApiServerClient apiServerClient;

    @Mock
    private ChzzkChannelClient chzzkChannelClient;

    @Mock
    private ExecutorService virtualThreadExecutor;

    private DailyFollowerCollectorScheduler scheduler;

    @BeforeEach
    void setUp() {
        Mockito.lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(virtualThreadExecutor).execute(any(Runnable.class));

        scheduler = new DailyFollowerCollectorScheduler(
            apiServerClient,
            chzzkChannelClient,
            virtualThreadExecutor
        );
    }

    @Test
    @DisplayName("정기 수집 시 대상 채널의 팔로워를 수집하여 API 서버에 전송한다")
    void collectDailyFollowersSuccess() {
        List<String> targetChannels = List.of("ch1", "ch2");
        when(apiServerClient.fetchFollowerTargetChannels()).thenReturn(Optional.of(targetChannels));
        when(chzzkChannelClient.fetchFollowerCount("ch1")).thenReturn(Optional.of(15000));
        when(chzzkChannelClient.fetchFollowerCount("ch2")).thenReturn(Optional.of(20000));

        scheduler.collectDailyFollowers();

        ArgumentCaptor<List<FollowerSnapshotRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiServerClient).sendFollowerSnapshots(captor.capture());

        List<FollowerSnapshotRecord> records = captor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).channelId()).isEqualTo("ch1");
        assertThat(records.get(0).followerCount()).isEqualTo(15000);
        assertThat(records.get(0).snapshotDate()).isEqualTo(LocalDate.now().minusDays(1));

        assertThat(records.get(1).channelId()).isEqualTo("ch2");
        assertThat(records.get(1).followerCount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("대상 채널 목록이 비어있으면 수집을 수행하지 않는다")
    void collectDailyFollowersEmptyTargets() {
        when(apiServerClient.fetchFollowerTargetChannels()).thenReturn(Optional.of(List.of()));

        scheduler.collectDailyFollowers();

        verify(chzzkChannelClient, never()).fetchFollowerCount(any());
        verify(apiServerClient, never()).sendFollowerSnapshots(any());
    }
}
