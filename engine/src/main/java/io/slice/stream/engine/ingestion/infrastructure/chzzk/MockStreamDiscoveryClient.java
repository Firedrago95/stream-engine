package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class MockStreamDiscoveryClient implements StreamDiscoveryClient {

    @Override
    public List<StreamTarget> fetchTopLiveStreams(int limit) {
        log.info("[부하 테스트] {}개의 더미 방을 생성합니다.", limit);

        return IntStream.range(1, limit)
            .mapToObj(i -> new StreamTarget(
                "mock_channel_" + i,
                "dummy_streamer_" + i,
                "mock_session_" + i,
                (long) i,
                "부하 테스트 방송 " + i,
                1000,
                "",
                "test",
                Instant.now()
            ))
            .toList();
    }

    @Override
    public List<StreamTarget> fetchLiveStreams(java.util.Set<String> channelIds) {
        log.info("[부하 테스트] 순위 밖 {}개의 더미 방 상태를 유지합니다.", channelIds.size());
        
        return channelIds.stream()
            .map(id -> new StreamTarget(
                id,
                "dummy_streamer_rankout",
                "mock_session_rankout",
                (long) id.hashCode(),
                "부하 테스트 순위 밖 방송 " + id,
                50,
                "",
                "test",
                Instant.now()
            ))
            .toList();
    }
}
