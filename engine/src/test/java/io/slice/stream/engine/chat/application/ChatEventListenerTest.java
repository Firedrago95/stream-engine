package io.slice.stream.engine.chat.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatEventListenerTest {

    @Mock
    private ChatManager chatManager;

    @Mock
    private ActiveStreamProvider activeStreamProvider;

    @Mock
    private TargetStreamPool targetStreamPool;

    @Mock
    private StreamDiscoveryClient streamDiscoveryClient;

    @Mock
    private ApiServerClient apiServerClient;

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Test
    void 엔진_시작_시_API_서버에서_타겟을_가져와_TargetStreamPool에_동기화하고_활성_스트림을_재개한다() {
        StreamTarget target = new StreamTarget("stream1", "채널명", "chat1", 1L, "제목", 100, "url", "카테고리", Instant.EPOCH);
        List<StreamTarget> activeTargets = List.of(target);

        when(apiServerClient.fetchTargetChannels()).thenReturn(List.of("ch1", "ch2"));
        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(activeTargets);

        chatEventListener.initActiveStreams();

        verify(targetStreamPool).syncTargets(Set.of("ch1", "ch2"));
        verify(chatManager).manageStreams(eq(Set.of(target)), eq(Collections.emptySet()));
    }

    @Test
    void 엔진_시작_시_활성_스트림이_없으면_ChatManager를_호출하지_않는다() {
        when(apiServerClient.fetchTargetChannels()).thenReturn(Collections.emptyList());
        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(List.of());

        chatEventListener.initActiveStreams();

        verify(chatManager, never()).manageStreams(any(), any());
    }

    @Test
    void StreamChangedEvent_수신_시_타겟_채널에_대해서만_0초_Lazy_Fetching_후_웹소켓을_연결한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "침착맨", null, 1L, "title1", 100, "thumb1.jpg", "소통", null);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "비타겟", null, 2L, "title2", 50, "thumb2.jpg", "게임", null);
        Set<StreamTarget> newStreamTargets = Set.of(streamTarget1, streamTarget2);

        StreamTarget detailedTarget1 = new StreamTarget("ch1", "침착맨", "chatCh1", 1L, "title1", 100, "thumb1.jpg", "소통", Instant.EPOCH);

        when(targetStreamPool.isTarget("ch1")).thenReturn(true);
        when(targetStreamPool.isTarget("ch2")).thenReturn(false);
        when(streamDiscoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(detailedTarget1));

        StreamChangedEvent event = new StreamChangedEvent(newStreamTargets, Collections.emptySet(), Instant.now());

        chatEventListener.handleStreamChangedEvent(event);

        verify(streamDiscoveryClient).fetchLiveStreams(Set.of("ch1"));
        verify(streamDiscoveryClient, never()).fetchLiveStreams(Set.of("ch2"));
        verify(chatManager).manageStreams(Set.of(detailedTarget1), Collections.emptySet());
    }

    @Test
    void StreamChangedEvent_수신_시_비타겟_채널만_존재하면_상세_조회_및_웹소켓_연결을_건너뛴다() {
        StreamTarget normalStream = new StreamTarget("ch_normal", "일반인", null, 10L, "제목", 5, "thumb.jpg", "소통", null);
        when(targetStreamPool.isTarget("ch_normal")).thenReturn(false);

        StreamChangedEvent event = new StreamChangedEvent(Set.of(normalStream), Collections.emptySet(), Instant.now());

        chatEventListener.handleStreamChangedEvent(event);

        verify(streamDiscoveryClient, never()).fetchLiveStreams(any());
        verify(chatManager, never()).manageStreams(any(), any());
    }

    @Test
    void StreamChangedEvent_수신_시_종료된_스트림은_웹소켓_해제를_호출한다() {
        StreamTarget closedStream = new StreamTarget("stream3", "c", "c", 3L, "t", 0, "u", "c", Instant.EPOCH);
        Set<StreamTarget> closedStreams = Set.of(closedStream);
        StreamChangedEvent event = new StreamChangedEvent(Collections.emptySet(), closedStreams, Instant.now());

        chatEventListener.handleStreamChangedEvent(event);

        verify(chatManager).manageStreams(Collections.emptySet(), closedStreams);
    }
}
