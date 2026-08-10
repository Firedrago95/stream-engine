package io.slice.stream.engine.ingestion.infrastructure.apiServer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ApiServerClientTest {

    private MockRestServiceServer mockServer;
    private ApiServerClient apiServerClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    private final String syncPath = "/api/v1/sync/streams/test-slug";
    private final String metaPath = "/api/v1/sync/streams/meta-test-slug";
    private final String summaryPath = "/api/v1/streams/{streamId}/summaries";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        apiServerClient = new ApiServerClient(builder.build(), syncPath, metaPath, summaryPath);
    }

    @Test
    void 방송_동기화_요청시_정확한_경로와_헤더로_데이터를_전송해야_한다() throws Exception {
        // given
        List<StreamSyncRequest> requests = List.of(
            new StreamSyncRequest("ch1", "live1", "침착맨", "제목1", "thumb1.jpg", 1000,"소통")
        );

        mockServer.expect(requestTo("http://localhost:8080" + syncPath))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json(objectMapper.writeValueAsString(requests)))
            .andRespond(withSuccess());

        // when & then
        assertThatNoException().isThrownBy(() -> apiServerClient.syncStreams(requests));
        mockServer.verify();
    }

    @Test
    void 서버가_에러를_응답해도_예외를_밖으로_던지지_않아야_한다() {
        // given
        mockServer.expect(requestTo("http://localhost:8080" + syncPath))
            .andRespond(withServerError());

        // when & then
        assertThatNoException().isThrownBy(() -> apiServerClient.syncStreams(List.of()));
    }
}
