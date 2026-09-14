package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final ChatManager chatService;
    private final ActiveStreamProvider activeStreamProvider;
    private final TargetStreamPool targetStreamPool;
    private final StreamDiscoveryClient streamDiscoveryClient;
    private final ApiServerClient apiServerClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initActiveStreams() {
        try {
            List<String> targets = apiServerClient.fetchTargetChannels();
            if (!targets.isEmpty()) {
                targetStreamPool.syncTargets(new HashSet<>(targets));
            }
        } catch (Exception e) {
            log.warn("[Init] 기동 중 타겟 명단 초기화 실패: {}", e.getMessage());
        }

        List<StreamTarget> activeTargets = activeStreamProvider.getActiveStreamTargets();

        if (!activeTargets.isEmpty()) {
            log.info("[Init] 엔진 시작 감지: 기존 {}개의 스트림 수집을 재개합니다.", activeTargets.size());
            chatService.manageStreams(new HashSet<>(activeTargets), Collections.emptySet());
        } else {
            log.info("[Init] 현재 활성 상태인 스트림이 없습니다.");
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void syncTargetsPeriodically() {
        List<String> targets = apiServerClient.fetchTargetChannels();
        if (!targets.isEmpty()) {
            targetStreamPool.syncTargets(new HashSet<>(targets));
        }
    }

    @EventListener
    public void handleStreamChangedEvent(StreamChangedEvent event) {
        if (!event.closedStreams().isEmpty()) {
            chatService.manageStreams(Collections.emptySet(), event.closedStreams());
        }

        if (!event.newStreams().isEmpty()) {
            Set<StreamTarget> targetStreams = new HashSet<>();
            for (StreamTarget stream : event.newStreams()) {
                if (targetStreamPool.isTarget(stream.channelId())) {
                    targetStreams.add(stream);
                } else {
                    log.info("[Targeting] 비타겟 방송 감지. 웹소켓 연결을 건너뜁니다. channelId: {}", stream.channelId());
                }
            }

            if (!targetStreams.isEmpty()) {
                Set<String> channelIds = targetStreams.stream()
                    .map(StreamTarget::channelId)
                    .collect(Collectors.toSet());

                List<StreamTarget> detailedTargets = streamDiscoveryClient.fetchLiveStreams(channelIds);
                if (!detailedTargets.isEmpty()) {
                    chatService.manageStreams(new HashSet<>(detailedTargets), Collections.emptySet());
                }
            }
        }
    }
}
