package io.slice.stream.apiserver.stream.targeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetStreamerServiceTest {

    @Mock
    private TargetStreamerRepository targetStreamerRepository;

    @Mock
    private JpaStreamRepository streamRepository;

    @InjectMocks
    private TargetStreamerService targetStreamerService;

    @Test
    void 활성_수동_공식_채널과_검증된_정규_활동_스트리머_목록이_중복없이_정상_병합된다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);
        TargetStreamerEntity custom = new TargetStreamerEntity("ch_custom", "인기 스트리머", TargetType.CUSTOM, true);

        StreamerLeaderboardProjection p1 = createProjection("ch_custom");
        StreamerLeaderboardProjection p2 = createProjection("ch_trend1");
        StreamerLeaderboardProjection p3 = createProjection("ch_trend2");

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(official, custom));
        when(streamRepository.findTopStreamersWith30dAvg(any(Instant.class), eq(5), eq(300)))
            .thenReturn(List.of(p1, p2, p3));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).containsExactly("ch_official", "ch_custom", "ch_trend1", "ch_trend2");
        verify(streamRepository, never()).findTopStreamIdsByConcurrentUserCount(any());
    }

    @Test
    void 정규_활동_스트리머_데이터가_없을_경우_실시간_시청자수_기반으로_fallback_동작한다() {
        TargetStreamerEntity official = new TargetStreamerEntity("ch_official", "공식 채널", TargetType.STATIC, true);

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(official));
        when(streamRepository.findTopStreamersWith30dAvg(any(Instant.class), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(streamRepository.findTopStreamIdsByConcurrentUserCount(PageRequest.of(0, 300)))
            .thenReturn(List.of("ch_fallback1", "ch_fallback2"));

        List<String> results = targetStreamerService.getActiveTargetChannelIds();

        assertThat(results).containsExactly("ch_official", "ch_fallback1", "ch_fallback2");
        verify(streamRepository).findTopStreamIdsByConcurrentUserCount(PageRequest.of(0, 300));
    }

    private StreamerLeaderboardProjection createProjection(String streamId) {
        return new StreamerLeaderboardProjection() {
            @Override public String getStreamId() { return streamId; }
            @Override public String getStreamerName() { return "스트리머_" + streamId; }
            @Override public String getLiveTitle() { return "방송 방제"; }
            @Override public String getProfileImageUrl() { return "https://img.png"; }
            @Override public String getCategoryName() { return "종합게임"; }
            @Override public boolean getIsLive() { return true; }
            @Override public Instant getLastUpdateAt() { return Instant.now(); }
            @Override public int getConcurrentUserCount() { return 1000; }
            @Override public int getAverageViewers() { return 1000; }
        };
    }
}
