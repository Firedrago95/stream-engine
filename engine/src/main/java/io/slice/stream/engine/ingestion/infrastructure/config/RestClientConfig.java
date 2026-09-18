package io.slice.stream.engine.ingestion.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class RestClientConfig {

    @Value("${chzzk.api.base-url}")
    String chzzkApiBaseUrl;

    @Value("${chzzk.game-api.base-url}")
    String chzzkGameApiBaseUrl;

    @Value("${api-server.host}")
    private String apiServerHost;

    @Value("${api-server.header}")
    private String apiServerHeader;

    @Value("${api-server.secret}")
    private String apiServerSecret;

    @Value("${api-server.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;

    @Value("${api-server.read-timeout-seconds:15}")
    private int readTimeoutSeconds;

    @Bean
    @Primary
    public RestClient chzzkApiRestClient(ChzzkHeaderInterceptor chzzkHeaderInterceptor) {
        return RestClient.builder()
            .baseUrl(chzzkApiBaseUrl)
            .requestInterceptor(chzzkHeaderInterceptor)
            .build();
    }

    @Bean
    public RestClient chzzkGameApiClient(ChzzkHeaderInterceptor chzzkHeaderInterceptor) {
        return RestClient.builder()
            .baseUrl(chzzkGameApiBaseUrl)
            .requestInterceptor(chzzkHeaderInterceptor)
            .build();
    }

    @Bean
    public RestClient apiServerRestClient() {
        return RestClient.builder()
            .baseUrl(apiServerHost)
            .defaultHeader(apiServerHeader, apiServerSecret)
            .defaultHeader("Content-Type", "application/json")
            .requestFactory(createApiServerRequestFactory())
            .build();
    }

    private JdkClientHttpRequestFactory createApiServerRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return factory;
    }
}
