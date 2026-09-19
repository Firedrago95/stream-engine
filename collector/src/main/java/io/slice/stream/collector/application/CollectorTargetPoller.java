package io.slice.stream.collector.application;

import io.slice.stream.collector.domain.LiveStatusClient;
import io.slice.stream.collector.domain.TargetStreamReader;
import io.slice.stream.core.model.StreamTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorTargetPoller {

    private final TargetStreamReader targetStreamReader;
    private final LiveStatusClient liveStatusClient;
    private final ChatManager chatManager;

    @Scheduled(fixedRateString = "${collector.polling.interval-ms:15000}")
    public void pollTargets() {
        try {
            Set<String> targetChannels = targetStreamReader.getTargetChannels();
            if (targetChannels.isEmpty()) {
                handleEmptyTargets();
                return;
            }

            List<StreamTarget> openStreams = liveStatusClient.fetchOpenStreams(targetChannels);
            syncCollectorStreams(targetChannels, openStreams);
        } catch (Exception e) {
            log.error("[타겟 동기화 오류] 타겟 스트림 상태 확인 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    private void handleEmptyTargets() {
        Set<String> activeIds = chatManager.getActiveChannelIds();
        if (!activeIds.isEmpty()) {
            Set<StreamTarget> closedTargets = toClosedTargets(activeIds);
            chatManager.manageStreams(Set.of(), closedTargets);
            log.info("[타겟 전체 해제] 타겟 풀이 비어있어 모든 수집기를 종료합니다. 종료 건수: {}", closedTargets.size());
        }
    }

    private void syncCollectorStreams(Set<String> targetChannels, List<StreamTarget> openStreams) {
        Map<String, StreamTarget> openStreamMap = openStreams.stream()
            .collect(Collectors.toMap(StreamTarget::channelId, target -> target, (existing, replace) -> existing));

        Set<StreamTarget> missingTargets = openStreams.stream()
            .filter(target -> !chatManager.isCollecting(target.channelId(), target.chatChannelId()))
            .collect(Collectors.toSet());

        Set<String> activeChannelIds = chatManager.getActiveChannelIds();
        Set<String> closedChannelIds = activeChannelIds.stream()
            .filter(channelId -> !targetChannels.contains(channelId) || !openStreamMap.containsKey(channelId))
            .collect(Collectors.toSet());

        Set<StreamTarget> closedTargets = toClosedTargets(closedChannelIds);

        if (!missingTargets.isEmpty() || !closedTargets.isEmpty()) {
            log.info("[수집기 동기화] 신규 연결 대상: {}건, 종료 대상: {}건", missingTargets.size(), closedTargets.size());
            chatManager.manageStreams(missingTargets, closedTargets);
        }
    }

    private Set<StreamTarget> toClosedTargets(Set<String> channelIds) {
        return channelIds.stream()
            .map(channelId -> new StreamTarget(channelId, null, null, 0L, null, 0, null, null, null))
            .collect(Collectors.toSet());
    }
}
