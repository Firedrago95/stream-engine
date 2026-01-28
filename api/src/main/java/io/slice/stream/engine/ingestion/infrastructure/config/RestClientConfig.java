package io.slice.stream.engine.ingestion.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${chzzk.api.base-url}")
    String chzzkApiBaseUrl;

    @Value("${chzzk.game-api.base-url}")
    String chzzkGameApiBaseUrl;

    // 1. [기존] 방송 정보 조회용 클라이언트 (api.chzzk.naver.com)
    @Bean
    @Primary
    public RestClient chzzkApiRestClient() {
        return RestClient.builder()
            .baseUrl(chzzkApiBaseUrl)
            .build();
    }

    // 2. 토큰 발급 전용 클라이언트 (comm-api.game.naver.com)
    @Bean
    public RestClient chzzkGameApiClient() {
        return RestClient.builder()
            .baseUrl(chzzkGameApiBaseUrl)
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build();
    }
}
