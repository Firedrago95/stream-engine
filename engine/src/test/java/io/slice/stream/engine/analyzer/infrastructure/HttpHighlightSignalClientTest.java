package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HttpHighlightSignalClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HttpHighlightSignalClient httpHighlightSignalClient;

    private static final String HIGHLIGHT_PATH = "/api/v1/signals/secret-path";

    @BeforeEach
    void setUp() {
        httpHighlightSignalClient = new HttpHighlightSignalClient(restClient, HIGHLIGHT_PATH);
    }

    @Test
    void send_메서드_호출시_RestClient의_Post_체인이_올바르게_실행되어야_한다() {
        // given
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "PEAK", Instant.now(), 100L)
        );

        // RestClient의 Fluent API 체이닝 모킹
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(HIGHLIGHT_PATH)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyList())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

        // when
        httpHighlightSignalClient.send(signals);

        // then
        verify(restClient).post();
        verify(requestBodyUriSpec).uri(HIGHLIGHT_PATH);
        verify(requestBodySpec).body(signals); // 객체가 직접 전달되는지 확인
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void 신호_리스트가_비어있으면_통신을_시도하지_않아야_한다() {
        // when
        httpHighlightSignalClient.send(Collections.emptyList());

        // then
        verify(restClient, never()).post();
    }

    @Test
    void 전송_중_예외가_발생해도_상위로_던지지_않고_로그만_남겨야_한다() {
        // given
        List<AnalysisSignal> signals = List.of(new AnalysisSignal("stream1", "PEAK", Instant.now(), 50L));

        when(restClient.post()).thenThrow(new RuntimeException("Connection Refused"));

        // when & then
        assertThatNoException().isThrownBy(() -> httpHighlightSignalClient.send(signals));
    }
}
