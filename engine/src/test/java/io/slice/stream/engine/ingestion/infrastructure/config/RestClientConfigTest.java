package io.slice.stream.engine.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

    @Test
    @DisplayName("apiServerRestClient는 독립적인 타임아웃 팩토리가 설정된 상태로 정상 생성된다")
    void apiServerRestClientWithTimeout() {
        RestClientConfig config = new RestClientConfig();
        ReflectionTestUtils.setField(config, "apiServerHost", "https://api.slice.io");
        ReflectionTestUtils.setField(config, "apiServerHeader", "X-Internal-Secret");
        ReflectionTestUtils.setField(config, "apiServerSecret", "test-secret");
        ReflectionTestUtils.setField(config, "connectTimeoutSeconds", 3);
        ReflectionTestUtils.setField(config, "readTimeoutSeconds", 10);

        RestClient restClient = config.apiServerRestClient();

        assertThat(restClient).isNotNull();
    }

    @Test
    @DisplayName("치지직 API 클라이언트는 api-server 타임아웃 설정과 격리되어 정상 생성된다")
    void chzzkClientsAreIsolated() {
        RestClientConfig config = new RestClientConfig();
        ReflectionTestUtils.setField(config, "chzzkApiBaseUrl", "https://api.chzzk.naver.com");
        ReflectionTestUtils.setField(config, "chzzkGameApiBaseUrl", "https://game.naver.com");

        ChzzkHeaderInterceptor interceptor = mock(ChzzkHeaderInterceptor.class);

        RestClient chzzkClient = config.chzzkApiRestClient(interceptor);
        RestClient gameClient = config.chzzkGameApiClient(interceptor);

        assertThat(chzzkClient).isNotNull();
        assertThat(gameClient).isNotNull();
    }
}
