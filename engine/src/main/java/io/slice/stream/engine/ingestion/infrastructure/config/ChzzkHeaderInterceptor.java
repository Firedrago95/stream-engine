package io.slice.stream.engine.ingestion.infrastructure.config;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChzzkHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final ChromeVersionManager chromeVersionManager;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.set("User-Agent", chromeVersionManager.getUserAgent());
        headers.set("sec-ch-ua", chromeVersionManager.getSecChUa());
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"macOS\"");
        headers.set("Origin", "https://chzzk.naver.com");
        headers.set("Referer", "https://chzzk.naver.com/");

        return execution.execute(request, body);
    }
}
