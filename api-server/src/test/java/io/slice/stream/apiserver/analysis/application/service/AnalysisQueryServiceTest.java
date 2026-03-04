package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
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
class AnalysisQueryServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private AnalysisQueryService analysisQueryService;

    @Test
    void 특정_스트림의_최근_분석_데이터를_조회하면_DTO_형태로_변환하여_반환한다() {
        // given
        String streamId = "test-stream";
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of(streamId, "PEAK", now, 100L)
        );
        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(signals);

        // when
        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(100L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");
        assertThat(response.dataPoints().get(0).timestamp()).isEqualTo(now.toEpochMilli());
    }

    @Test
    void 조회된_데이터가_없으면_빈_리스트를_가진_응답_객체를_반환한다() {
        // given
        String streamId = "empty-stream";
        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(List.of());

        // when
        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).isEmpty();
    }
}
