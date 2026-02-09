package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatManager chatService;

    @EventListener
    public void handleStreamChangedEvent(StreamChangedEvent event) {
        chatService.manageStreams(
            event.newStreamIds(),
            event.closedStreamIds()
        );
    }
}
