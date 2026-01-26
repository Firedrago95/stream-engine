package io.slice.stream.engine.chat.infrastructure.chzzk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChatAccessResponse;
import io.slice.stream.engine.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkApiClientTest {

    private ChzzkApiClient chzzkApiClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_URL = "https://comm-api.game.naver.com";
    private static final String TOKEN_URL = "/nng_main/v1/chats/access-token?channelId=testChannel&chatType=STREAMING";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        chzzkApiClient = new ChzzkApiClient(builder.build());
    }

    @Test
    void 유효한_채널ID로_AccessToken을_성공적으로_가져온다() throws Exception {
        // given
        String expectedToken = "mockAccessToken";
        ChatAccessResponse mockResponse = new ChatAccessResponse(
            new ChatAccessResponse.Content(expectedToken)
        );
        String responseBody = objectMapper.writeValueAsString(mockResponse);

        mockServer.expect(requestTo(BASE_URL + TOKEN_URL))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        String accessToken = chzzkApiClient.getAccessToken("testChannel");

        // then
        mockServer.verify();
        assertThat(accessToken).isEqualTo(expectedToken);
    }

    @Test
    void API_응답의_body가_null일_경우_BusinessException을_던진다() {
        // given
        mockServer.expect(requestTo(BASE_URL + TOKEN_URL))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON)); // body가 null인 경우

        // when & then
        assertThatThrownBy(() -> chzzkApiClient.getAccessToken("testChannel"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
    }

    @Test
    void API_응답의_content가_null일_경우_BusinessException을_던진다() throws Exception {
        // given
        ChatAccessResponse mockResponse = new ChatAccessResponse(null);
        String responseBody = objectMapper.writeValueAsString(mockResponse);

        mockServer.expect(requestTo(BASE_URL + TOKEN_URL))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> chzzkApiClient.getAccessToken("testChannel"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
    }
    
    @Test
    void API_응답의_accessToken이_null일_경우_BusinessException을_던진다() throws Exception {
        // given
        ChatAccessResponse mockResponse = new ChatAccessResponse(
            new ChatAccessResponse.Content(null)
        );
        String responseBody = objectMapper.writeValueAsString(mockResponse);

        mockServer.expect(requestTo(BASE_URL + TOKEN_URL))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
            
        // when & then
        assertThatThrownBy(() -> chzzkApiClient.getAccessToken("testChannel"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
    }
    
    @Test
    void API_호출이_실패하면_RestClientException이_발생하고_핸들링된다() {
        // given
        mockServer.expect(requestTo(BASE_URL + TOKEN_URL))
            .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> chzzkApiClient.getAccessToken("testChannel"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("치지직 API를 통해 accessToken을 받아오지 못했습니다. Status: Internal Server Error");
    }
}
