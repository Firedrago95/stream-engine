package io.slice.stream.engine.chat.domain;

import org.springframework.stereotype.Component;

@Component
public interface ChatCollectorFactory {

    ChatCollector start(String streamId);
}
