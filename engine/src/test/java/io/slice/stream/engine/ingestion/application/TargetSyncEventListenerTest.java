package io.slice.stream.engine.ingestion.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TargetSyncEventListenerTest {

    private TargetStreamPool targetStreamPool;
    private ApiServerClient apiServerClient;
    private TargetSyncEventListener listener;

    @BeforeEach
    void setUp() {
        targetStreamPool = mock(TargetStreamPool.class);
        apiServerClient = mock(ApiServerClient.class);
        listener = new TargetSyncEventListener(targetStreamPool, apiServerClient);
    }

    @Test
    @DisplayName("애플리케이션 준비 완료 시 API 서버에서 타겟 채널 목록을 조회하여 동기화한다")
    void syncTargetsOnInit() {
        when(apiServerClient.fetchTargetChannels()).thenReturn(Optional.of(List.of("ch1", "ch2")));

        listener.initTargets();

        verify(targetStreamPool).syncTargets(Set.of("ch1", "ch2"));
    }

    @Test
    @DisplayName("API 서버 조회 결과가 없으면 동기화를 수행하지 않는다")
    void doNotSyncWhenApiServerReturnsEmpty() {
        when(apiServerClient.fetchTargetChannels()).thenReturn(Optional.empty());

        listener.initTargets();

        verify(targetStreamPool, never()).syncTargets(any());
    }

    @Test
    @DisplayName("주기적 스케줄링 호출 시에도 타겟을 정상 동기화한다")
    void syncTargetsPeriodically() {
        when(apiServerClient.fetchTargetChannels()).thenReturn(Optional.of(List.of("ch_trend_1")));

        listener.syncTargetsPeriodically();

        verify(targetStreamPool).syncTargets(Set.of("ch_trend_1"));
    }
}
