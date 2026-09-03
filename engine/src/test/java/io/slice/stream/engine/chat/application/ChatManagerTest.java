package io.slice.stream.engine.chat.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.core.model.StreamTarget;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatManagerTest {

    @Mock
    private ChatCollectorFactory chatCollectorFactory;

    @Mock
    private ExecutorService virtualThreadExecutor;

    @Mock
    private ActiveStreamProvider activeStreamProvider;

    @InjectMocks
    private ChatManager chatManager;

    @Mock
    private ChatCollector mockCollector;

    @BeforeEach
    void setUp() {
        Mockito.lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(virtualThreadExecutor).submit(any(Runnable.class));
    }

    @Test
    void 새로운_스트림에_대해_채팅_수집을_시작해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "스트리머1", "chat1", 1L, "제목1", 100, "https://thumb.com/1.jpg", "음악", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("stream2", "스트리머2", "chat2", 2L, "제목2", 200, "https://thumb.com/2.jpg", "게임", Instant.EPOCH);
        Set<StreamTarget> newStreamTargets = Set.of(streamTarget1, streamTarget2);
        Set<StreamTarget> closedStreams = Collections.emptySet();

        when(chatCollectorFactory.start(streamTarget1)).thenReturn(mockCollector);
        when(chatCollectorFactory.start(streamTarget2)).thenReturn(mockCollector);

        // when
        chatManager.manageStreams(newStreamTargets, closedStreams);

        // then
        verify(chatCollectorFactory, timeout(2000).times(1)).start(streamTarget1);
        verify(chatCollectorFactory, timeout(2000).times(1)).start(streamTarget2);
    }

    @Test
    void 종료된_스트림에_대해_채팅_수집을_중단해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "스트리머1", "chat1", 1L, "제목1", 100, "https://thumb.com/1.jpg", "게임", Instant.EPOCH);
        Set<StreamTarget> initialStreamTargets = Set.of(streamTarget1);
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTarget1)).thenReturn(collectorToStop);

        // 먼저 등록
        chatManager.manageStreams(initialStreamTargets, Collections.emptySet());
        verify(chatCollectorFactory, timeout(2000)).start(streamTarget1);

        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        Set<StreamTarget> closedStreams = Set.of(streamTarget1);

        // when
        chatManager.manageStreams(newStreamTargets, closedStreams);

        // then
        verify(collectorToStop).disconnect();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_동시에_처리해야_한다() {
        // given
        StreamTarget streamTargetToClose = new StreamTarget("streamToClose", "종료스트리머", "chatClose", 99L, "종료제목", 999, "https://thumb.com/close.jpg", "소통", Instant.EPOCH);
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTargetToClose)).thenReturn(collectorToStop);
        chatManager.manageStreams(Set.of(streamTargetToClose), Collections.emptySet());
        verify(chatCollectorFactory, timeout(2000)).start(streamTargetToClose);

        StreamTarget streamTargetNew1 = new StreamTarget("streamNew1", "신규1", "chatNew1", 101L, "신규제목1", 111, "https://thumb.com/new1.jpg", "게임", Instant.EPOCH);
        StreamTarget streamTargetNew2 = new StreamTarget("streamNew2", "신규2", "chatNew2", 102L, "신규제목2", 222, "https://thumb.com/new2.jpg", "먹방", Instant.EPOCH);
        Set<StreamTarget> newStreamTargets = Set.of(streamTargetNew1, streamTargetNew2);

        Set<StreamTarget> closedStreams = Set.of(streamTargetToClose);

        ChatCollector newCollector = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTargetNew1)).thenReturn(newCollector);
        when(chatCollectorFactory.start(streamTargetNew2)).thenReturn(newCollector);

        // when
        chatManager.manageStreams(newStreamTargets, closedStreams);

        // then
        verify(chatCollectorFactory, timeout(2000).times(1)).start(streamTargetNew1);
        verify(chatCollectorFactory, timeout(2000).times(1)).start(streamTargetNew2);
        verify(collectorToStop).disconnect();
    }

    @Test
    void 스트림에_변화가_없을_경우_아무_동작도_하지_않아야_한다() {
        // given
        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        Set<StreamTarget> closedStreams = Collections.emptySet();

        // when
        chatManager.manageStreams(newStreamTargets, closedStreams);

        // then
        verify(chatCollectorFactory, never()).start(any(StreamTarget.class));
    }

    @Test
    void 관리하지_않는_스트림의_종료_요청은_무시해야_한다() {
        // given
        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        StreamTarget nonExistent = new StreamTarget("nonExistentStreamId", "non", "non", 999L, "non", 0, "non", "non", Instant.EPOCH);
        Set<StreamTarget> closedStreams = Set.of(nonExistent);

        // when
        chatManager.manageStreams(newStreamTargets, closedStreams);

        // then
        verify(mockCollector, never()).disconnect();
    }

    @Test
    void chatChannelId가_null이거나_공백이면_수집을_시작하지_않는다() {
        // given
        StreamTarget nullChat = new StreamTarget("stream1", "과로사1", null, 1L, "제목1", 100, "thumb1.jpg", "게임", Instant.EPOCH);
        StreamTarget blankChat = new StreamTarget("stream2", "하루야치에", "   ", 2L, "제목2", 200, "thumb2.jpg", "게임", Instant.EPOCH);
        StreamTarget validChat = new StreamTarget("stream3", "정상스트리머", "chat3", 3L, "제목3", 300, "thumb3.jpg", "게임", Instant.EPOCH);

        when(chatCollectorFactory.start(validChat)).thenReturn(mockCollector);

        // when
        chatManager.manageStreams(Set.of(nullChat, blankChat, validChat), Collections.emptySet());

        // then
        verify(chatCollectorFactory, never()).start(nullChat);
        verify(chatCollectorFactory, never()).start(blankChat);
        verify(chatCollectorFactory, timeout(2000).times(1)).start(validChat);
    }

    @Test
    void 동일_채널이_종료와_신규로_동시_유입될_경우_종료를_먼저_수행하고_새_수집기를_연결해야_한다() {
        // given
        String channelId = "channel1";
        StreamTarget oldTarget = new StreamTarget(channelId, "침착맨", "chatOld", 100L, "어제 방송", 100, "thumb.jpg", "소통", Instant.EPOCH);
        StreamTarget newTarget = new StreamTarget(channelId, "침착맨", "chatNew", 200L, "오늘 방송", 200, "thumb.jpg", "소통", Instant.EPOCH);

        ChatCollector oldCollector = mock(ChatCollector.class);
        ChatCollector newCollector = mock(ChatCollector.class);

        when(chatCollectorFactory.start(oldTarget)).thenReturn(oldCollector);
        when(chatCollectorFactory.start(newTarget)).thenReturn(newCollector);

        chatManager.manageStreams(Set.of(oldTarget), Collections.emptySet());

        // when
        chatManager.manageStreams(Set.of(newTarget), Set.of(oldTarget));

        // then
        org.mockito.InOrder inOrder = Mockito.inOrder(oldCollector, chatCollectorFactory);
        inOrder.verify(oldCollector).disconnect();
        inOrder.verify(chatCollectorFactory, timeout(2000)).start(newTarget);
    }

    @Test
    void reconcile_호출_시_누락된_스트림은_연결하고_종료된_스트림은_해제해야_한다() {
        // given
        StreamTarget existingTarget = new StreamTarget("channel1", "스트리머1", "chat1", 1L, "제목1", 100, "thumb1.jpg", "소통", Instant.EPOCH);
        StreamTarget zombieTarget = new StreamTarget("zombieChannel", "좀비", "chatZombie", 99L, "좀비제목", 50, "thumbZ.jpg", "소통", Instant.EPOCH);
        StreamTarget missingTarget = new StreamTarget("missingChannel", "누락스트리머", "chatMissing", 2L, "누락제목", 200, "thumbM.jpg", "게임", Instant.EPOCH);

        ChatCollector existingCollector = mock(ChatCollector.class);
        ChatCollector zombieCollector = mock(ChatCollector.class);
        ChatCollector missingCollector = mock(ChatCollector.class);

        when(chatCollectorFactory.start(existingTarget)).thenReturn(existingCollector);
        when(chatCollectorFactory.start(zombieTarget)).thenReturn(zombieCollector);
        when(chatCollectorFactory.start(missingTarget)).thenReturn(missingCollector);

        chatManager.manageStreams(Set.of(existingTarget, zombieTarget), Collections.emptySet());

        // 현재 활성 목록에는 existingTarget과 missingTarget만 존재 (zombieTarget은 종료됨)
        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(List.of(existingTarget, missingTarget));

        // when
        chatManager.reconcile();

        // then
        verify(zombieCollector).disconnect();
        verify(chatCollectorFactory, timeout(2000).times(1)).start(missingTarget);
        verify(chatCollectorFactory, times(1)).start(existingTarget);
    }

    @Test
    void 활성_스트림_목록이_비어_있으면_기존의_모든_수집기를_종료해야_한다() {
        StreamTarget streamTarget = new StreamTarget("channel1", "스트리머1", "chat1", 1L, "제목1", 100, "thumb1.jpg", "소통", Instant.EPOCH);
        ChatCollector collector = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTarget)).thenReturn(collector);

        chatManager.manageStreams(Set.of(streamTarget), Collections.emptySet());

        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(Collections.emptyList());

        chatManager.reconcile();

        verify(collector).disconnect();
    }
}
