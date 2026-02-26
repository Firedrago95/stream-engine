package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamQueryServiceTest {

    @Mock
    StreamService streamService;

    @Mock
    AnalysisRepository analysisRepository;

    @InjectMocks
    StreamQueryService streamQueryService;

    @Test
    void 분석_신호가_있는_방송은_ANALYZING_상태여야_한다() {
        // given
        StreamSyncRequest request = new StreamSyncRequest("ch1", "침착맨", "제목", "url", "게임");
        when(streamService.getAllStreams()).thenReturn(List.of(request));

        AnalysisSignal signal = new AnalysisSignal("ch1", "NORMAL", Instant.now(), 10L);
        when(analysisRepository.findRecentSignals("ch1", 1)).thenReturn(List.of(signal));

        // when
        List<StreamResponse> result = streamQueryService.getBrowseList();

        // then
        assertThat(result.get(0).status()).isEqualTo("ANALYZING");
    }

    @Test
    void 신호가_없는_방송은_LIVE_상태여야_한다() {
        // given
        StreamSyncRequest request = new StreamSyncRequest("ch2", "주호민", "제목", "url", "소통");
        when(streamService.getAllStreams()).thenReturn(List.of(request));
        when(analysisRepository.findRecentSignals("ch2", 1)).thenReturn(List.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowseList();

        // then
        assertThat(result.get(0).status()).isEqualTo("LIVE");
    }
}
