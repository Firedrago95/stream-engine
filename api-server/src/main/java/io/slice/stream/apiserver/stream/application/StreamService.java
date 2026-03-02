package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private volatile Map<String, StreamSyncRequest> streamCache = Map.of();

    public void syncAll(List<StreamSyncRequest> streams) {
        HashMap<String, StreamSyncRequest> newCache = new HashMap<>();
        streams.forEach(s -> newCache.put(s.streamId(), s));
        this.streamCache = newCache;
        log.info("[Sync] 현재 서버 캐시 방송 수 : {}", streamCache.size());
    }

    public List<StreamSyncRequest> getAllStreams() {
        return new ArrayList<>(streamCache.values());
    }
}
