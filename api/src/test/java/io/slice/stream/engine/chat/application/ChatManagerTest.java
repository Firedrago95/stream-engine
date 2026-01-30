package io.slice.stream.engine.chat.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.core.model.StreamTarget;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatManagerTest {

    @Mock
    private ChatCollectorFactory chatCollectorFactory;

    @InjectMocks
    private ChatManager chatManager;

    @Mock
    private ChatCollector mockCollector;

    @BeforeEach
    void setUp() {}

    @Test
    void 새로운_스트림에_대해_채팅_수집을_시작해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "name1", "chat1", 1L, "title1", 100);
        StreamTarget streamTarget2 = new StreamTarget("stream2", "name2", "chat2", 2L, "title2", 200);
        Set<StreamTarget> newStreamTargets = Set.of(streamTarget1, streamTarget2);
        Set<String> closedChatChannelIds = Collections.emptySet();

        when(chatCollectorFactory.start(streamTarget1)).thenReturn(mockCollector);
        when(chatCollectorFactory.start(streamTarget2)).thenReturn(mockCollector);

        // when
        chatManager.manageStreams(newStreamTargets, closedChatChannelIds);

        // then
        verify(chatCollectorFactory, times(1)).start(streamTarget1);
        verify(chatCollectorFactory, times(1)).start(streamTarget2);
    }

    @Test
    void 종료된_스트림에_대해_채팅_수집을_중단해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "name1", "chat1", 1L, "title1", 100);
        Set<StreamTarget> initialStreamTargets = Set.of(streamTarget1);
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTarget1)).thenReturn(collectorToStop);
        chatManager.manageStreams(initialStreamTargets, Collections.emptySet());

        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        Set<String> closedChatChannelIds = Set.of(streamTarget1.chatChannelId());

        // when
        chatManager.manageStreams(newStreamTargets, closedChatChannelIds);

        // then
        verify(collectorToStop).disconnect();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_동시에_처리해야_한다() {
        // given
        StreamTarget streamTargetToClose = new StreamTarget("streamToClose", "nameClose", "chatClose", 99L, "titleClose", 999);
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTargetToClose)).thenReturn(collectorToStop);
        chatManager.manageStreams(Set.of(streamTargetToClose), Collections.emptySet());

        StreamTarget streamTargetNew1 = new StreamTarget("streamNew1", "nameNew1", "chatNew1", 101L, "titleNew1", 111);
        StreamTarget streamTargetNew2 = new StreamTarget("streamNew2", "nameNew2", "chatNew2", 102L, "titleNew2", 222);
        Set<StreamTarget> newStreamTargets = Set.of(streamTargetNew1, streamTargetNew2);
        Set<String> closedChatChannelIds = Set.of(streamTargetToClose.chatChannelId());

        ChatCollector newCollector = mock(ChatCollector.class);
        when(chatCollectorFactory.start(streamTargetNew1)).thenReturn(newCollector);
        when(chatCollectorFactory.start(streamTargetNew2)).thenReturn(newCollector);

        // when
        chatManager.manageStreams(newStreamTargets, closedChatChannelIds);

        // then
        verify(chatCollectorFactory, times(1)).start(streamTargetNew1);
        verify(chatCollectorFactory, times(1)).start(streamTargetNew2);
        verify(collectorToStop).disconnect();
    }

    @Test
    void 스트림에_변화가_없을_경우_아무_동작도_하지_않아야_한다() {
        // given
        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        Set<String> closedChatChannelIds = Collections.emptySet();

        // when
        chatManager.manageStreams(newStreamTargets, closedChatChannelIds);

        // then
        verify(chatCollectorFactory, never()).start(any(StreamTarget.class));
    }

    @Test
    void 관리하지_않는_스트림의_종료_요청은_무시해야_한다() {
        // given
        Set<StreamTarget> newStreamTargets = Collections.emptySet();
        Set<String> closedChatChannelIds = Set.of("nonExistentChatChannel");

        // when
        chatManager.manageStreams(newStreamTargets, closedChatChannelIds);

        // then
        verify(mockCollector, never()).disconnect();
    }
}
