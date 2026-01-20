package io.slice.stream.engine.chat.application;

import static org.mockito.Mockito.verify;

import io.slice.stream.engine.core.event.StreamChangedEvent;
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
        Set<String> newStreamIds = Set.of("stream1", "stream2");
        Set<String> closedStreamIds = Set.of("stream3");
        StreamChangedEvent event = new StreamChangedEvent(newStreamIds, closedStreamIds);

        // when
        chatEventListener.handleStreamChangedEvent(event);

        // then
        verify(chatManager).manageStreams(newStreamIds, closedStreamIds);
    }
}
