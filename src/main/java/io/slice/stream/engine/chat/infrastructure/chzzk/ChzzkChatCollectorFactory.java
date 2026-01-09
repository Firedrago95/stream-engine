package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import org.springframework.stereotype.Component;

@Component
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    @Override
    public ChatCollector start(String streamId) {

        return null;
    }
}
