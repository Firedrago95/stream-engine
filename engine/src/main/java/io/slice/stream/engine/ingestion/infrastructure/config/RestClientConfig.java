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

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    // 1. [기존] 방송 정보 조회용 클라이언트 (api.chzzk.naver.com)
    @Bean
    @Primary
    public RestClient chzzkApiRestClient() {
        return RestClient.builder()
            .baseUrl(chzzkApiBaseUrl)
            .defaultHeader("User-Agent", USER_AGENT)
            .defaultHeader("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
            .defaultHeader("sec-ch-ua-mobile", "?0")
            .defaultHeader("sec-ch-ua-platform", "\"macOS\"")
            .build();
    }

    // 2. 토큰 발급 전용 클라이언트 (comm-api.game.naver.com)
    @Bean
    public RestClient chzzkGameApiClient() {
        return RestClient.builder()
            .baseUrl(chzzkGameApiBaseUrl)
            .defaultHeader("User-Agent", USER_AGENT)
            .defaultHeader("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
            .defaultHeader("sec-ch-ua-mobile", "?0")
            .defaultHeader("sec-ch-ua-platform", "\"macOS\"")
            .build();
    }

    // 3. 우리 API 서버와 통신할 전용 클라이언트
    @Bean
    public RestClient apiServerRestClient() {
        return RestClient.builder()
            .baseUrl(apiServerHost)
            .defaultHeader(apiServerHeader, apiServerSecret)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
