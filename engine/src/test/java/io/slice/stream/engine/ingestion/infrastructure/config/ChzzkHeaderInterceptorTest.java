package io.slice.stream.engine.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkHeaderInterceptorTest {

    @Mock
    private ChromeVersionManager chromeVersionManager;

    @Mock
    private ClientHttpRequestExecution execution;

    private ChzzkHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChzzkHeaderInterceptor(chromeVersionManager);
    }

    @Test
    void 요청_시_최신_크롬_헤더와_Origin_Referer가_올바르게_주입된다() throws IOException {
        given(chromeVersionManager.getUserAgent())
            .willReturn("Mozilla/5.0 Chrome/152.0.0.0 Safari/537.36");
        given(chromeVersionManager.getSecChUa())
            .willReturn("\"Chromium\";v=\"152\", \"Google Chrome\";v=\"152\"");

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "https://api.chzzk.naver.com/service/v1/lives");
        byte[] body = new byte[0];

        interceptor.intercept(request, body, execution);

        HttpHeaders headers = request.getHeaders();
        assertThat(headers.getFirst("User-Agent")).isEqualTo("Mozilla/5.0 Chrome/152.0.0.0 Safari/537.36");
        assertThat(headers.getFirst("sec-ch-ua")).isEqualTo("\"Chromium\";v=\"152\", \"Google Chrome\";v=\"152\"");
        assertThat(headers.getFirst("sec-ch-ua-mobile")).isEqualTo("?0");
        assertThat(headers.getFirst("sec-ch-ua-platform")).isEqualTo("\"macOS\"");
        assertThat(headers.getFirst("Origin")).isEqualTo("https://chzzk.naver.com");
        assertThat(headers.getFirst("Referer")).isEqualTo("https://chzzk.naver.com/");
    }
}
