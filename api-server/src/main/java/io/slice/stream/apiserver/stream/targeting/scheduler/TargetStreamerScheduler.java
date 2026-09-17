package io.slice.stream.apiserver.stream.targeting.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TargetStreamerScheduler {

    @Scheduled(cron = "${targeting.cache.evict.cron:0 20 4 * * *}", zone = "Asia/Seoul")
    @CacheEvict(value = "targetChannels", allEntries = true)
    public void evictTargetChannelsCache() {
        log.info("[Targeting] 새벽 정기 타겟 채널 캐시 초기화 완료");
    }
}
