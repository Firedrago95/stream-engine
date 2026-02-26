package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final Map<String, StreamSyncRequest> streamCache = new ConcurrentHashMap<>();

    public void syncAll(List<StreamSyncRequest> streams) {
        streams.forEach(s -> streamCache.put(s.channelId(), s));
        log.info("[Sync] 현재 서버 캐시 방송 수 : {}", streamCache.size());
    }

    public List<StreamSyncRequest> getAllStreams() {
        return new ArrayList<>(streamCache.values());
    }
}
