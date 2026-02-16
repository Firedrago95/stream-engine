package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HttpHighlightSignalClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private HttpHighlightSignalClient httpHighlightSignalClient;

    private static final String API_SERVER_URL = "http://localhost:8081/internal/highlights";
    private static final String ENGINE_SECRET = "test-engine-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(httpHighlightSignalClient, "apiServerUrl", API_SERVER_URL);
        ReflectionTestUtils.setField(httpHighlightSignalClient, "engineSecret", ENGINE_SECRET);
    }

    @Test
    void send_메서드_호출시_올바른_HttpRequest로_HttpClient가_호출되어야_한다() throws IOException, InterruptedException {
        // given
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "PEAK", Instant.now()),
            new AnalysisSignal("stream2", "NORMAL", Instant.now())
        );
        String jsonBody = """
            [
              {"streamId":"stream1","signalType":"PEAK","timestamp":"2026-02-13T10:00:00Z"},
              {"streamId":"stream2","signalType":"NORMAL","timestamp":"2026-02-13T10:00:00Z"}
            ]
            """;

        when(objectMapper.writeValueAsString(signals)).thenReturn(jsonBody);

        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        // when
        httpHighlightSignalClient.send(signals);

        // then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.uri().toString()).isEqualTo(API_SERVER_URL);
        assertThat(capturedRequest.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(capturedRequest.headers().firstValue("X-SL-ENGINE-TOKEN")).contains(ENGINE_SECRET);
        assertThat(capturedRequest.bodyPublisher()).isPresent();
    }

    @Test
    void send_메서드_실행_중_예외가_발생해도_정상_종료되어야_하며_로그를_남긴다() throws IOException, InterruptedException {
        // given
        List<AnalysisSignal> signals = List.of(new AnalysisSignal("stream1", "EXPLODED", Instant.now()));

        when(objectMapper.writeValueAsString(signals)).thenThrow(new RuntimeException("Serialization Error"));

        // when & then
        assertThatNoException().isThrownBy(() -> httpHighlightSignalClient.send(signals));

        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void send_메서드에서_httpClient_send_실패_시_예외를_던지지_않고_로그를_남긴다() throws IOException, InterruptedException {
        // given
        List<AnalysisSignal> signals = List.of(new AnalysisSignal("stream1", "PEAK", Instant.now()));
        String jsonBody = "[]";

        when(objectMapper.writeValueAsString(signals)).thenReturn(jsonBody);
        when(httpClient.send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Network Error"));

        // when & then
        assertThatNoException().isThrownBy(() ->
            httpHighlightSignalClient.send(signals));
    }

}
