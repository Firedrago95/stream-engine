package io.slice.stream.engine.ingestion.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${url.base-url}")
    String baseUrl;

    @Bean
    public RestClient chzzkLiveFetchRestClient() {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
}
