package io.slice.stream.engine.chat.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
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

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Test
    void 엔진_시작_시_활성_스트림이_존재하면_ChatManager의_manageStreams를_호출하여_재개해야_한다() {
        // given
        StreamTarget target = new StreamTarget("stream1", "채널명", "chat1", 1L, "제목", 100, "url", "카테고리", Instant.EPOCH);
        List<StreamTarget> activeTargets = List.of(target);
        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(activeTargets);

        // when
        chatEventListener.initActiveStreams();

        // then
        verify(chatManager).manageStreams(eq(Set.of(target)), eq(Collections.emptySet()));
    }

    @Test
    void 엔진_시작_시_활성_스트림이_없으면_ChatManager를_호출하지_않는다() {
        // given
        when(activeStreamProvider.getActiveStreamTargets()).thenReturn(List.of());

        // when
        chatEventListener.initActiveStreams();

        // then
        verify(chatManager, never()).manageStreams(any(), any());
    }

    @Test
    void StreamChangedEvent를_수신하면_ChatManager의_manageStreams를_호출해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "침착맨", "chat1", 1L, "title1", 100, "https://thumb.com/1.jpg", "소통", Instant.EPOCH);
        Set<StreamTarget> newStreamTargets = Set.of(streamTarget1);
        StreamTarget closedStream = new StreamTarget("stream3", "c", "c", 3L, "t", 0, "u", "c", Instant.EPOCH);
        Set<StreamTarget> closedStreams = Set.of(closedStream);
        StreamChangedEvent event = new StreamChangedEvent(newStreamTargets, closedStreams, Instant.now());

        // when
        chatEventListener.handleStreamChangedEvent(event);

        // then
        verify(chatManager).manageStreams(newStreamTargets, closedStreams);
    }
}
