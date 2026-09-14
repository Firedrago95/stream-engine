package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class StreamerLeaderboardQueryServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private TargetStreamerService targetStreamerService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private StreamerLeaderboardQueryService leaderboardQueryService;

    @Test
    @DisplayName("캐시가 비어있을 때 최근 30일 평균 시청자 기준으로 정산하고 결과를 반환한다")
    void getLeaderboard_whenCacheEmpty_calculatesAndReturns() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(StreamerLeaderboardQueryService.REDIS_LEADERBOARD_KEY)).willReturn(null);

        StreamerLeaderboardProjection p1 = createProjection("ch_1", "울프", "토크", 30074, 1000, true, Instant.now());
        StreamerLeaderboardProjection p2 = createProjection("ch_2", "풍월량", "종합게임", 11634, 0, false, Instant.now().minusSeconds(7200));

        given(streamRepository.findTopStreamersWith30dAvg(any(Instant.class), eq(100)))
            .willReturn(List.of(p1, p2));
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(List.of("ch_1", "ch_2"));
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard(null);

        assertThat(result).hasSize(2);

        StreamResponse first = result.get(0);
        assertThat(first.streamId()).isEqualTo("ch_1");
        assertThat(first.streamerName()).isEqualTo("울프");
        assertThat(first.averageViewers()).isEqualTo(30074);
        assertThat(first.status()).isEqualTo(StreamStatus.ANALYZING);
        assertThat(first.concurrentUserCount()).isEqualTo(1000);

        StreamResponse second = result.get(1);
        assertThat(second.streamId()).isEqualTo("ch_2");
        assertThat(second.streamerName()).isEqualTo("풍월량");
        assertThat(second.averageViewers()).isEqualTo(11634);
        assertThat(second.status()).isEqualTo(StreamStatus.OFFLINE);
        assertThat(second.concurrentUserCount()).isZero();
    }

    @Test
    @DisplayName("키워드가 캐시된 Top 100에 포함되어 있으면 DB 조회 없이 캐시에서 필터링하여 반환한다")
    void getLeaderboard_withKeyword_whenMatchInCache_returnsFromCacheWithoutDb() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(StreamerLeaderboardQueryService.REDIS_LEADERBOARD_KEY)).willReturn("[{\"streamId\":\"ch_wolf\"}]");

        StreamResponse cachedResponse1 = new StreamResponse("ch_wolf", "울프", "이전 방제", "https://img.png", "토크", 0, StreamStatus.OFFLINE, 30074);
        StreamResponse cachedResponse2 = new StreamResponse("ch_pung", "풍월량", "이전 방제", "https://img.png", "게임", 0, StreamStatus.OFFLINE, 11634);
        given(jsonMapper.readValue(anyString(), any(TypeReference.class)))
            .willReturn(List.of(cachedResponse1, cachedResponse2));

        given(streamRepository.findActiveStreamsByStreamIds(any(), any()))
            .willReturn(Collections.emptyList());
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(Collections.emptyList());
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard("울프");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamId()).isEqualTo("ch_wolf");
        assertThat(result.get(0).streamerName()).isEqualTo("울프");
        assertThat(result.get(0).averageViewers()).isEqualTo(30074);
    }

    @Test
    @DisplayName("키워드가 캐시에 없으면 DB 검색 쿼리를 실행하여 반환한다")
    void getLeaderboard_withKeyword_whenNotInCache_queriesDatabase() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(StreamerLeaderboardQueryService.REDIS_LEADERBOARD_KEY)).willReturn(null);

        given(streamRepository.findTopStreamersWith30dAvg(any(Instant.class), eq(100)))
            .willReturn(Collections.emptyList());

        StreamerLeaderboardProjection projection =
            createProjection("ch_search", "침착맨", "토크", 25000, 20000, true, Instant.now());

        given(streamRepository.searchTopStreamersWith30dAvg(eq("침착맨"), any(Instant.class), eq(50)))
            .willReturn(List.of(projection));
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(Collections.emptyList());
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard("침착맨");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamerName()).isEqualTo("침착맨");
        assertThat(result.get(0).averageViewers()).isEqualTo(25000);
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.LIVE);
    }

    @Test
    @DisplayName("캐시가 존재할 때 캐시에서 조회 후 현재 실시간 방송 상태를 동기화하여 반환한다")
    void getLeaderboard_whenCacheHit_syncsRealtimeStatus() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(StreamerLeaderboardQueryService.REDIS_LEADERBOARD_KEY)).willReturn("[{\"streamId\":\"ch_wolf\"}]");

        StreamResponse cachedResponse = new StreamResponse("ch_wolf", "울프", "이전 방제", "https://img.png", "토크", 0, StreamStatus.OFFLINE, 30074);
        given(jsonMapper.readValue(anyString(), any(TypeReference.class)))
            .willReturn(List.of(cachedResponse));

        StreamEntity activeWolf = new StreamEntity("ch_wolf", "울프");
        activeWolf.heartbeat("울프", "롤드컵 중계", "https://img.png", "LCK", 45000);

        given(streamRepository.findActiveStreamsByStreamIds(any(), any()))
            .willReturn(List.of(activeWolf));
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(Collections.emptyList());
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard(null);

        assertThat(result).hasSize(1);
        StreamResponse item = result.get(0);
        assertThat(item.streamId()).isEqualTo("ch_wolf");
        assertThat(item.streamerName()).isEqualTo("울프");
        assertThat(item.liveTitle()).isEqualTo("롤드컵 중계");
        assertThat(item.status()).isEqualTo(StreamStatus.LIVE);
        assertThat(item.concurrentUserCount()).isEqualTo(45000);
        assertThat(item.averageViewers()).isEqualTo(30074);
    }

    private StreamerLeaderboardProjection createProjection(
        String streamId,
        String streamerName,
        String categoryName,
        int averageViewers,
        int concurrentUserCount,
        boolean isLive,
        Instant lastUpdateAt
    ) {
        return new StreamerLeaderboardProjection() {
            @Override public String getStreamId() { return streamId; }
            @Override public String getStreamerName() { return streamerName; }
            @Override public String getLiveTitle() { return "방송 방제"; }
            @Override public String getProfileImageUrl() { return "https://img.png"; }
            @Override public String getCategoryName() { return categoryName; }
            @Override public boolean getIsLive() { return isLive; }
            @Override public Instant getLastUpdateAt() { return lastUpdateAt; }
            @Override public int getConcurrentUserCount() { return concurrentUserCount; }
            @Override public int getAverageViewers() { return averageViewers; }
        };
    }
}
