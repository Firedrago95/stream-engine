package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
            virtualThreadExecutor,
            3,
            0L
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

    @Test
    @DisplayName("스냅샷 전송이 1차 실패 후 2차 재시도에서 성공하면 정상 완료된다")
    void collectDailyFollowersRetrySuccessOnSecondAttempt() {
        List<String> targetChannels = List.of("ch1");
        when(apiServerClient.fetchFollowerTargetChannels()).thenReturn(Optional.of(targetChannels));
        when(chzzkChannelClient.fetchFollowerCount("ch1")).thenReturn(Optional.of(15000));

        doThrow(new RuntimeException("API 서버 연결 일시 오류"))
            .doNothing()
            .when(apiServerClient).sendFollowerSnapshots(any());

        scheduler.collectDailyFollowers();

        verify(apiServerClient, times(2)).sendFollowerSnapshots(any());
    }

    @Test
    @DisplayName("스냅샷 전송이 최대 재시도(3회)를 초과하여 모두 실패하면 예외를 상위로 전파한다")
    void collectDailyFollowersRetryFailAllThrowsException() {
        List<String> targetChannels = List.of("ch1");
        when(apiServerClient.fetchFollowerTargetChannels()).thenReturn(Optional.of(targetChannels));
        when(chzzkChannelClient.fetchFollowerCount("ch1")).thenReturn(Optional.of(15000));

        doThrow(new RuntimeException("500 Internal Server Error"))
            .when(apiServerClient).sendFollowerSnapshots(any());

        assertThatThrownBy(() -> scheduler.collectDailyFollowers())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("3회 재시도 후에도 최종 실패했습니다");

        verify(apiServerClient, times(3)).sendFollowerSnapshots(any());
    }

    @Test
    @DisplayName("대상 채널 조회가 1차 실패 후 2차 재시도에서 성공하면 정상 수집을 진행한다")
    void fetchTargetChannelsRetrySuccess() {
        List<String> targetChannels = List.of("ch1");
        when(apiServerClient.fetchFollowerTargetChannels())
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(targetChannels));
        when(chzzkChannelClient.fetchFollowerCount("ch1")).thenReturn(Optional.of(15000));

        scheduler.collectDailyFollowers();

        verify(apiServerClient, times(2)).fetchFollowerTargetChannels();
        verify(apiServerClient, times(1)).sendFollowerSnapshots(any());
    }
}
