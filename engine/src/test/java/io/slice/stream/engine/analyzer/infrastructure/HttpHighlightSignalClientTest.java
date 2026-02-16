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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    void send_메서드_호출시_올바른_HttpRequest로_비동기_호출되어야_한다() throws Exception {
        // given
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "PEAK", Instant.now())
        );
        String jsonBody = "[{\"streamId\":\"stream1\"}]";

        when(objectMapper.writeValueAsString(signals)).thenReturn(jsonBody);

        CompletableFuture<HttpResponse> mockFuture = CompletableFuture.completedFuture(mock(HttpResponse.class));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockFuture);

        // when
        httpHighlightSignalClient.send(signals);

        // then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertAll(
            () -> assertThat(capturedRequest.uri().toString()).isEqualTo(API_SERVER_URL),
            () -> assertThat(capturedRequest.headers().firstValue("Content-Type")).contains("application/json"),
            () -> assertThat(capturedRequest.headers().firstValue("X-SL-ENGINE-TOKEN")).contains(ENGINE_SECRET),
            () -> assertThat(capturedRequest.method()).isEqualTo("POST")
        );
    }

    @Test
    void 직렬화_단계에서_예외가_발생해도_httpClient를_호출하지_않고_정상_종료되어야_한다() throws Exception {
        // given
        List<AnalysisSignal> signals = List.of(new AnalysisSignal("stream1", "ERROR", Instant.now()));
        when(objectMapper.writeValueAsString(signals)).thenThrow(new RuntimeException("Serialization Failed"));

        // when & then
        assertThatNoException().isThrownBy(() -> httpHighlightSignalClient.send(signals));
        verify(httpClient, never()).sendAsync(any(), any());
    }

    @Test
    void 비동기_전송_중_네트워크_에러가_발생해도_전체_프로세스는_예외를_던지지_않는다() throws Exception {
        // given
        List<AnalysisSignal> signals = List.of(new AnalysisSignal("stream1", "PEAK", Instant.now()));
        when(objectMapper.writeValueAsString(signals)).thenReturn("[]");

        CompletableFuture<HttpResponse<Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Network Down"));

        when(httpClient.sendAsync(any(), any())).thenReturn(failedFuture);

        // when & then
        assertThatNoException().isThrownBy(() -> httpHighlightSignalClient.send(signals));
        verify(httpClient).sendAsync(any(), any());
    }

    private void assertAll(Runnable... assertions) {
        for (Runnable assertion : assertions) {
            assertion.run();
        }
    }
}
