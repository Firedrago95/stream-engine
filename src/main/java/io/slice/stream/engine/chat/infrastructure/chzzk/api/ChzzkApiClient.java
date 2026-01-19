package io.slice.stream.engine.chat.infrastructure.chzzk.api;

import io.slice.stream.engine.chat.infrastructure.chzzk.dto.response.ChatAccessResponse;
import io.slice.stream.engine.global.error.BusinessException;
import io.slice.stream.engine.global.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ChzzkApiClient {

    private final RestClient restClient;

    public ChzzkApiClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String getAccessToken(String chatChannelId) {
        ChatAccessResponse chatAccessResponse = restClient
                .get()
                .uri("https://comm-api.game.naver.com/nng_main/v1/chats/access-token?channelId={channelId}&chatType=STREAMING", chatChannelId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ChatAccessResponse.class);

        if (chatAccessResponse == null) {
            throw new BusinessException(ErrorCode.INVALID_CHANNEL_ID, "잘못된 채널 id입니다. accessToken을 찾을 수 없습니다.");
        }

        String accessToken = chatAccessResponse.content().accessToken();
        return accessToken;
    }
}
