package io.slice.stream.engine.chat.application;

import static org.mockito.Mockito.verify;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
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

    @InjectMocks
    private ChatEventListener chatEventListener;

    @Test
    void StreamChangedEvent를_수신하면_ChatManager의_manageStreams를_호출해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("stream1", "침착맨", "chat1", 1L, "title1", 100, "https://thumb.com/1.jpg", "소통");
        StreamTarget streamTarget2 = new StreamTarget("stream2", "풍월량", "chat2", 2L, "title2", 200, "https://thumb.com/2.jpg", "게임");
        Set<StreamTarget> newStreamTargets = Set.of(streamTarget1, streamTarget2);
        Set<String> closedStreamIds = Set.of("stream3");
        StreamChangedEvent event = new StreamChangedEvent(newStreamTargets, closedStreamIds);

        // when
        chatEventListener.handleStreamChangedEvent(event);

        // then
        verify(chatManager).manageStreams(newStreamTargets, closedStreamIds);
    }
}
