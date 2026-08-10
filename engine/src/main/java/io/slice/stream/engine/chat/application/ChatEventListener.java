package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatManager chatService;
    private final ActiveStreamProvider activeStreamProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void initActiveStreams() {
        List<StreamTarget> activeTargets = activeStreamProvider.getActiveStreamTargets();

        if (!activeTargets.isEmpty()) {
            log.info("[Init] 엔진 시작 감지: 기존 {}개의 스트림 수집을 재개합니다.", activeTargets.size());

            chatService.manageStreams(new HashSet<>(activeTargets), Collections.emptySet());
        } else {
            log.info("[Init] 현재 활성 상태인 스트림이 없습니다.");
        }
    }

    @EventListener
    public void handleStreamChangedEvent(StreamChangedEvent event) {
        chatService.manageStreams(
            event.newStreams(),
            event.closedStreams()
        );
    }
}
