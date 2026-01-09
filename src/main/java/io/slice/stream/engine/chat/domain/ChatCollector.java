package io.slice.stream.engine.chat.domain;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatCollector {

    private final ChatClient chatClient;

    public void disconnect() {
    }
}
