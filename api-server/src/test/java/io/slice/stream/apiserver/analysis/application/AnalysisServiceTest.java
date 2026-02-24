package io.slice.stream.apiserver.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    void 신호_리스트를_받으면_각_신호를_내부_이벤트로_발행한다() {
        // given
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "PEAK", Instant.now(), 20),
            new AnalysisSignal("stream2", "NORMAL", Instant.now(), 5)
        );

        // when
        analysisService.processSignals(signals);

        // then
        verify(eventPublisher, times(2)).publishEvent(any(AnalysisSignal.class));
    }

    @Test
    void 빈_신호_리스트를_받으면_이벤트를_발행하지_않는다() {
        // given
        List<AnalysisSignal> signals = List.of();

        // when
        analysisService.processSignals(signals);

        // then
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void 특정_스트림의_최근_분석_데이터를_조회하면_DTO_형태로_변환하여_반환한다() {
        // given
        String streamId = "test-stream";
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of(streamId, "PEAK", now, 100L)
        );
        given(analysisRepository.findRecentSignals(streamId, 50)).willReturn(signals);

        // when
        AnalysisResponse response = analysisService.getRecentAnalysis(streamId);

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
        given(analysisRepository.findRecentSignals(streamId, 50)).willReturn(List.of());

        // when
        AnalysisResponse response = analysisService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).isEmpty();
    }
}
