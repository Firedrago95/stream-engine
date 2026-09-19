package io.slice.stream.collector.infrastructure.chzzk.api;

import io.slice.stream.collector.infrastructure.chzzk.dto.response.ChatAccessResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ChzzkApiClient {

    public static final String URL = "/nng_main/v1/chats/access-token?channelId={channelId}&chatType=STREAMING";

    private final RestClient restClient;

    public ChzzkApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public String getAccessToken(String chatChannelId) {
        try {
            ChatAccessResponse chatAccessResponse = restClient
                .get()
                .uri(URL, chatChannelId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("[치지직 토큰 API 오류] 상태 코드: {}, 채팅방: {}", response.getStatusCode(), chatChannelId);
                })
                .body(ChatAccessResponse.class);

            if (chatAccessResponse == null || chatAccessResponse.content() == null) {
                log.warn("[치지직 토큰 응답 없음] chatChannelId: {}", chatChannelId);
                return null;
            }

            return chatAccessResponse.content().accessToken();
        } catch (Exception e) {
            log.error("[치지직 토큰 발급 실패] chatChannelId: {}, 원인: {}", chatChannelId, e.getMessage());
            return null;
        }
    }
}
