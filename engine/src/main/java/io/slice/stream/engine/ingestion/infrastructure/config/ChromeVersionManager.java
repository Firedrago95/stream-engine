package io.slice.stream.engine.ingestion.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ChromeVersionManager {

    private final RestClient restClient;
    private final String versionApiUrl;
    private final AtomicReference<String> currentVersion;

    @Autowired
    public ChromeVersionManager(
        @Value("${chzzk.api.version-api-url:https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json}") String versionApiUrl,
        @Value("${chzzk.api.default-chrome-version:152.0.0.0}") String defaultChromeVersion
    ) {
        this(createDefaultRestClient(), versionApiUrl, defaultChromeVersion);
    }

    public ChromeVersionManager(
        RestClient.Builder restClientBuilder,
        String versionApiUrl,
        String defaultChromeVersion
    ) {
        this(restClientBuilder.build(), versionApiUrl, defaultChromeVersion);
    }

    public ChromeVersionManager(
        RestClient restClient,
        String versionApiUrl,
        String defaultChromeVersion
    ) {
        this.restClient = restClient;
        this.versionApiUrl = versionApiUrl;
        this.currentVersion = new AtomicReference<>(defaultChromeVersion);
    }

    private static RestClient createDefaultRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        refreshVersion();
    }

    @Scheduled(cron = "0 0 3 * * MON")
    public void refreshVersion() {
        try {
            ChromeVersionResponse response = restClient.get()
                .uri(versionApiUrl)
                .retrieve()
                .body(ChromeVersionResponse.class);

            if (response != null && response.channels() != null && response.channels().Stable() != null) {
                String version = response.channels().Stable().version();
                if (version != null && !version.isBlank()) {
                    currentVersion.set(version);
                    log.info("[크롬 버전 관리자] 최신 크롬 버전 갱신 완료: {}", version);
                }
            }
        } catch (Exception e) {
            log.warn("[크롬 버전 관리자] 최신 버전 조회 실패. 기본 버전({})을 유지합니다.", currentVersion.get(), e);
        }
    }

    public String getCurrentVersion() {
        return currentVersion.get();
    }

    public String getMajorVersion() {
        return currentVersion.get().split("\\.")[0];
    }

    public String getUserAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
            + currentVersion.get() + " Safari/537.36";
    }

    public String getSecChUa() {
        String major = getMajorVersion();
        return "\"Chromium\";v=\"" + major + "\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"" + major + "\"";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChromeVersionResponse(
        Channels channels
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Channels(
            Channel Stable
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Channel(
            String version
        ) {}
    }
}
