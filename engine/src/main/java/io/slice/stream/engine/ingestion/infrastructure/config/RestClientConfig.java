package io.slice.stream.engine.ingestion.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
            .build();
    }
}
