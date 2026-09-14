package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamerLeaderboardQueryServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private TargetStreamerService targetStreamerService;

    @InjectMocks
    private StreamerLeaderboardQueryService leaderboardQueryService;

    @Test
    @DisplayName("라이브 여부와 무관하게 순수 체급 순서로 리더보드를 반환하고 오프라인 스트리머는 OFFLINE과 시청자수 0으로 매핑된다")
    void getLeaderboard_success() {
        StreamEntity offlineTopStreamer = new StreamEntity("ch_offline", "오프라인대기업");
        offlineTopStreamer.heartbeat("오프라인대기업", "어제 방제", "https://img.com/1.png", "토크", 35000);
        offlineTopStreamer.markOffline();

        StreamEntity onlineStreamer = new StreamEntity("ch_online", "온라인중견");
        onlineStreamer.heartbeat("온라인중견", "오늘 생방송", "https://img.com/2.png", "종합게임", 8000);

        given(streamRepository.findAllStreamersForLeaderboard())
            .willReturn(List.of(offlineTopStreamer, onlineStreamer));
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(List.of("ch_offline", "ch_online"));
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard(null);

        assertThat(result).hasSize(2);

        StreamResponse first = result.get(0);
        assertThat(first.streamId()).isEqualTo("ch_offline");
        assertThat(first.streamerName()).isEqualTo("오프라인대기업");
        assertThat(first.status()).isEqualTo(StreamStatus.OFFLINE);
        assertThat(first.concurrentUserCount()).isZero();

        StreamResponse second = result.get(1);
        assertThat(second.streamId()).isEqualTo("ch_online");
        assertThat(second.streamerName()).isEqualTo("온라인중견");
        assertThat(second.status()).isEqualTo(StreamStatus.ANALYZING);
        assertThat(second.concurrentUserCount()).isEqualTo(8000);
    }

    @Test
    @DisplayName("키워드가 전달되면 검색용 리포지토리 메서드를 호출한다")
    void getLeaderboard_withKeyword() {
        StreamEntity streamer = new StreamEntity("ch_search", "침착맨");
        streamer.heartbeat("침착맨", "삼국지 토크", "https://img.com/3.png", "토크", 25000);

        given(streamRepository.searchAllStreamersForLeaderboard("침착맨"))
            .willReturn(List.of(streamer));
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(Collections.emptyList());
        given(analysisRepository.findChannelsWithRecentSignals(anySet(), any(Instant.class)))
            .willReturn(Collections.emptySet());

        List<StreamResponse> result = leaderboardQueryService.getLeaderboard("침착맨");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamerName()).isEqualTo("침착맨");
    }
}
