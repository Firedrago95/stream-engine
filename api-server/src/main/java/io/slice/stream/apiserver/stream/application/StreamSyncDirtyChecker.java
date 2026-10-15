package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamSyncDirtyChecker {

    private static final String KEY_PREFIX = "stream:sync:snapshot:";
    private static final int VIEWER_DELTA_ABSOLUTE_THRESHOLD = 30;
    private static final double VIEWER_DELTA_RATIO_THRESHOLD = 0.10;

    private final StringRedisTemplate redisTemplate;

    public List<StreamSyncRequest> filterDirtyRequests(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> keys = requests.stream()
                .map(req -> KEY_PREFIX + req.streamId())
                .toList();

            List<String> cachedSnapshots = redisTemplate.opsForValue().multiGet(keys);
            if (cachedSnapshots == null || cachedSnapshots.size() != requests.size()) {
                return requests;
            }

            List<StreamSyncRequest> dirtyRequests = new ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                StreamSyncRequest req = requests.get(i);
                String cached = cachedSnapshots.get(i);

                if (isDirty(req, cached)) {
                    dirtyRequests.add(req);
                }
            }

            return dirtyRequests;
        } catch (Exception e) {
            log.warn("[DirtyCheck] Redis 스냅샷 조회 실패, 전체를 Dirty로 간주합니다: {}", e.getMessage());
            return requests;
        }
    }

    public void updateSnapshots(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        try {
            Map<String, String> snapshotMap = new HashMap<>();
            for (StreamSyncRequest req : requests) {
                snapshotMap.put(KEY_PREFIX + req.streamId(), serializeSnapshot(req));
            }
            redisTemplate.opsForValue().multiSet(snapshotMap);
        } catch (Exception e) {
            log.warn("[DirtyCheck] Redis 스냅샷 갱신 실패: {}", e.getMessage());
        }
    }

    private static final String DELIMITER = "\u001F";

    private boolean isDirty(StreamSyncRequest req, String cachedSnapshot) {
        if (cachedSnapshot == null || cachedSnapshot.isBlank()) {
            return true;
        }

        String[] parts = cachedSnapshot.split(DELIMITER, -1);
        if (parts.length < 8) {
            return true;
        }

        String cachedLiveId = parts[0];
        String cachedStreamerName = parts[1];
        String cachedTitle = parts[2];
        String cachedProfileImageUrl = parts[3];
        String cachedCategory = parts[4];
        boolean cachedAdult = Boolean.parseBoolean(parts[5]);
        boolean cachedPaid = Boolean.parseBoolean(parts[6]);
        int cachedViewers = parseViewers(parts[7]);

        if (!Objects.equals(req.liveId(), cachedLiveId)) {
            return true;
        }
        if (!Objects.equals(req.streamerName(), cachedStreamerName)) {
            return true;
        }
        if (!Objects.equals(req.liveTitle(), cachedTitle)) {
            return true;
        }
        if (!Objects.equals(req.profileImageUrl(), cachedProfileImageUrl)) {
            return true;
        }
        if (!Objects.equals(req.categoryName(), cachedCategory)) {
            return true;
        }
        if (req.adult() != cachedAdult || req.paidPromotion() != cachedPaid) {
            return true;
        }

        return isViewerCountSignificantlyChanged(req.concurrentUserCount(), cachedViewers);
    }

    private boolean isViewerCountSignificantlyChanged(int current, int cached) {
        int delta = Math.abs(current - cached);
        if (delta < VIEWER_DELTA_ABSOLUTE_THRESHOLD) {
            return false;
        }
        if (cached == 0) {
            return current > 0;
        }
        double ratio = (double) delta / cached;
        return ratio >= VIEWER_DELTA_RATIO_THRESHOLD;
    }

    private int parseViewers(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String serializeSnapshot(StreamSyncRequest req) {
        return String.join(DELIMITER,
            req.liveId() != null ? req.liveId() : "",
            req.streamerName() != null ? req.streamerName() : "",
            req.liveTitle() != null ? req.liveTitle() : "",
            req.profileImageUrl() != null ? req.profileImageUrl() : "",
            req.categoryName() != null ? req.categoryName() : "",
            String.valueOf(req.adult()),
            String.valueOf(req.paidPromotion()),
            String.valueOf(req.concurrentUserCount())
        );
    }
}
