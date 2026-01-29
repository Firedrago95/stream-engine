package io.slice.stream.engine.chat.infrastructure.chzzk.api;

import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChatAccessResponse;
import io.slice.stream.engine.global.error.BusinessException;
import io.slice.stream.engine.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ChzzkApiClient {

    public static final String URL = "/nng_main/v1/chats/access-token?channelId={channelId}&chatType=STREAMING";

    private final RestClient restClient;

    public ChzzkApiClient(@Qualifier("chzzkGameApiClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String getAccessToken(String chatChannelId) {
        ChatAccessResponse chatAccessResponse = restClient
            .get()
            .uri(URL, chatChannelId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                // 응답 본문을 읽어옵니다 (한 번 읽으면 사라지므로 주의해야 하지만, 에러 상황이니 괜찮습니다)
                String responseBody = new String(response.getBody().readAllBytes());

                // 로그에 헤더와 본문을 모두 남깁니다.
                log.error("❌ 치지직 API 에러 발생!");
                log.error("Request URI: {}", request.getURI());
                log.error("Response Status: {}", response.getStatusCode());
                log.error("Response Body: {}", responseBody);

                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "치지직 API를 통해 accessToken을 받아오지 못했습니다. Status: " + response.getStatusText()
                + "/ streamId:" + chatChannelId);
            })
            .body(ChatAccessResponse.class);

        if (chatAccessResponse == null) {
            throw new BusinessException(ErrorCode.INVALID_CHANNEL_ID,
                "잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
        }

        if (chatAccessResponse.content() == null
            || chatAccessResponse.content().accessToken() == null) {
            throw new BusinessException(ErrorCode.INVALID_CHANNEL_ID,
                "잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
        }

        return chatAccessResponse.content().accessToken();
    }
}
