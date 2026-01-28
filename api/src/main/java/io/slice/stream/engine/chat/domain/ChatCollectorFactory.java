package io.slice.stream.engine.chat.domain;

import io.slice.stream.engine.core.model.StreamTarget;
import org.springframework.stereotype.Component;

@Component
public interface ChatCollectorFactory {

    ChatCollector start(StreamTarget streamTarget);
}
