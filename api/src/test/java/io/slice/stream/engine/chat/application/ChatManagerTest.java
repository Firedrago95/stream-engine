package io.slice.stream.engine.chat.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
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
        Set<String> newStreamIds = Set.of("stream1", "stream2");
        Set<String> closedStreamIds = Collections.emptySet();

        when(chatCollectorFactory.start("stream1")).thenReturn(mockCollector);
        when(chatCollectorFactory.start("stream2")).thenReturn(mockCollector);

        // when
        chatManager.manageStreams(newStreamIds, closedStreamIds);

        // then
        verify(chatCollectorFactory, times(1)).start("stream1");
        verify(chatCollectorFactory, times(1)).start("stream2");
    }

    @Test
    void 종료된_스트림에_대해_채팅_수집을_중단해야_한다() {
        // given
        Set<String> initialStreamIds = Set.of("stream1");
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start("stream1")).thenReturn(collectorToStop);
        chatManager.manageStreams(initialStreamIds, Collections.emptySet());

        Set<String> newStreamIds = Collections.emptySet();
        Set<String> closedStreamIds = Set.of("stream1");

        // when
        chatManager.manageStreams(newStreamIds, closedStreamIds);

        // then
        verify(collectorToStop).disconnect();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_동시에_처리해야_한다() {
        // given
        ChatCollector collectorToStop = mock(ChatCollector.class);
        when(chatCollectorFactory.start("streamToClose")).thenReturn(collectorToStop);
        chatManager.manageStreams(Set.of("streamToClose"), Collections.emptySet());

        Set<String> newStreamIds = Set.of("streamNew1", "streamNew2");
        Set<String> closedStreamIds = Set.of("streamToClose");

        ChatCollector newCollector = mock(ChatCollector.class);
        when(chatCollectorFactory.start("streamNew1")).thenReturn(newCollector);
        when(chatCollectorFactory.start("streamNew2")).thenReturn(newCollector);

        // when
        chatManager.manageStreams(newStreamIds, closedStreamIds);

        // then
        verify(chatCollectorFactory, times(1)).start("streamNew1");
        verify(chatCollectorFactory, times(1)).start("streamNew2");
        verify(collectorToStop).disconnect();
    }

    @Test
    void 스트림에_변화가_없을_경우_아무_동작도_하지_않아야_한다() {
        // given
        Set<String> newStreamIds = Collections.emptySet();
        Set<String> closedStreamIds = Collections.emptySet();

        // when
        chatManager.manageStreams(newStreamIds, closedStreamIds);

        // then
        verify(chatCollectorFactory, never()).start(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 관리하지_않는_스트림의_종료_요청은_무시해야_한다() {
        // given
        Set<String> newStreamIds = Collections.emptySet();
        Set<String> closedStreamIds = Set.of("nonExistentStream");

        // when
        chatManager.manageStreams(newStreamIds, closedStreamIds);

        // then
        verify(mockCollector, never()).disconnect();
    }
}
