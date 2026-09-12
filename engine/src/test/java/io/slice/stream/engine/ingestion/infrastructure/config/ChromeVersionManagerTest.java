package io.slice.stream.engine.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChromeVersionManagerTest {

    private MockRestServiceServer mockServer;
    private ChromeVersionManager chromeVersionManager;
    private final String versionApiUrl = "https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json";
    private final String defaultVersion = "152.0.0.0";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        chromeVersionManager = new ChromeVersionManager(builder, versionApiUrl, defaultVersion);
    }

    @Test
    void 구글_API_호출_성공_시_최신_크롬_버전으로_갱신된다() {
        String jsonResponse = """
            {
              "timestamp": "2026-09-12T00:00:00.000Z",
              "channels": {
                "Stable": {
                  "channel": "Stable",
                  "version": "153.0.7500.12",
                  "revision": "12345"
                }
              }
            }
            """;

        mockServer.expect(requestTo(versionApiUrl))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        chromeVersionManager.refreshVersion();

        mockServer.verify();
        assertThat(chromeVersionManager.getCurrentVersion()).isEqualTo("153.0.7500.12");
        assertThat(chromeVersionManager.getMajorVersion()).isEqualTo("153");
        assertThat(chromeVersionManager.getUserAgent()).contains("Chrome/153.0.7500.12");
        assertThat(chromeVersionManager.getSecChUa()).contains("\"Chromium\";v=\"153\"");
    }

    @Test
    void 구글_API_호출_실패_시_기본_버전이_유지된다() {
        mockServer.expect(requestTo(versionApiUrl))
            .andRespond(withServerError());

        chromeVersionManager.refreshVersion();

        mockServer.verify();
        assertThat(chromeVersionManager.getCurrentVersion()).isEqualTo(defaultVersion);
        assertThat(chromeVersionManager.getMajorVersion()).isEqualTo("152");
        assertThat(chromeVersionManager.getUserAgent()).contains("Chrome/152.0.0.0");
    }
}
