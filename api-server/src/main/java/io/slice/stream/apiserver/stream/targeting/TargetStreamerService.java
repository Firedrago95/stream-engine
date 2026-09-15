package io.slice.stream.apiserver.stream.targeting;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TargetStreamerService {

    private static final int DAYS_30 = 30;
    private static final int MIN_DAYS = 5;
    private static final int TARGET_STREAMER_LIMIT = 300;

    private final TargetStreamerRepository targetStreamerRepository;
    private final JpaStreamRepository streamRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "targetChannels", unless = "#result.isEmpty()")
    public List<String> getActiveTargetChannelIds() {
        Set<String> targetChannelIds = new LinkedHashSet<>();

        List<TargetStreamerEntity> staticAndCustomTargets = targetStreamerRepository.findAllByIsActiveTrue();
        for (TargetStreamerEntity entity : staticAndCustomTargets) {
            targetChannelIds.add(entity.getChannelId());
        }

        Instant thirtyDaysAgo = Instant.now().minus(DAYS_30, ChronoUnit.DAYS);
        List<StreamerLeaderboardProjection> verifiedStreamers =
            streamRepository.findTopStreamersWith30dAvg(thirtyDaysAgo, MIN_DAYS, TARGET_STREAMER_LIMIT);

        List<String> trendChannels = (verifiedStreamers != null && !verifiedStreamers.isEmpty())
            ? verifiedStreamers.stream().map(StreamerLeaderboardProjection::getStreamId).toList()
            : Collections.emptyList();

        if (trendChannels.isEmpty()) {
            log.info("[Targeting] 정규 활동 스트리머 데이터가 부족하여 실시간 시청자 수 기반으로 대체합니다.");
            trendChannels = streamRepository.findTopStreamIdsByConcurrentUserCount(PageRequest.of(0, TARGET_STREAMER_LIMIT));
        }

        if (trendChannels != null) {
            targetChannelIds.addAll(trendChannels);
        }

        log.info("[Targeting] 활성 타겟 채널 목록 조회 완료 (총 {}개)", targetChannelIds.size());
        return new ArrayList<>(targetChannelIds);
    }
}
