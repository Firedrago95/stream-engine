package io.slice.stream.collector.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.collector.domain.LiveStatusResult;
import io.slice.stream.core.model.StreamTarget;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.web.client.RestClient;

class ChzzkLiveStatusClientTest {

    private MockRestServiceServer mockServer;
    private ChzzkLiveStatusClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://api.chzzk.naver.com")
            .messageConverters(converters -> {
                converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
            });

        mockServer = MockRestServiceServer.bindTo(builder)
            .build(new UnorderedRequestExpectationManager());
        client = new ChzzkLiveStatusClient(builder.build(), Runnable::run);
    }

    @Test
    @DisplayName("치지직 API 응답에 따라 OPEN, CLOSED, FAILED 상태를 정확히 분리하여 반환한다")
    void separateOpenClosedAndFailedStatuses() {
        String openResponseJson = """
            {
                "content": {
                    "liveTitle": "풍월량 생방송",
                    "status": "OPEN",
                    "liveCategoryValue": "종합게임",
                    "concurrentUserCount": 15000,
                    "accumulateCount": 20000,
                    "openDate": "2026-10-16 00:00:00",
                    "chatChannelId": "chat_room_open",
                    "channelId": "ch_open",
                    "liveId": 12345,
                    "adult": false
                }
            }
            """;

        String closedResponseJson = """
            {
                "content": {
                    "liveTitle": "방송 종료됨",
                    "status": "CLOSE",
                    "liveCategoryValue": null,
                    "concurrentUserCount": 0,
                    "accumulateCount": 0,
                    "openDate": null,
                    "chatChannelId": null,
                    "channelId": "ch_closed",
                    "liveId": 12340,
                    "adult": false
                }
            }
            """;

        mockServer.expect(requestTo("https://api.chzzk.naver.com/polling/v2/channels/ch_open/live-status"))
            .andRespond(withSuccess(openResponseJson, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.chzzk.naver.com/polling/v2/channels/ch_closed/live-status"))
            .andRespond(withSuccess(closedResponseJson, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.chzzk.naver.com/polling/v2/channels/ch_failed/live-status"))
            .andRespond(withServerError());

        LiveStatusResult result = client.fetchLiveStatuses(Set.of("ch_open", "ch_closed", "ch_failed"));

        mockServer.verify();

        assertThat(result.openStreams()).hasSize(1);
        StreamTarget openTarget = result.openStreams().get(0);
        assertThat(openTarget.channelId()).isEqualTo("ch_open");
        assertThat(openTarget.chatChannelId()).isEqualTo("chat_room_open");
        assertThat(openTarget.channelName()).isEqualTo("ch_open");
        assertThat(openTarget.concurrentUserCount()).isEqualTo(15000);

        assertThat(result.closedChannelIds()).containsExactly("ch_closed");
        assertThat(result.failedChannelIds()).containsExactly("ch_failed");
    }

    @Test
    @DisplayName("조회 대상 채널 ID 집합이 비어있으면 빈 결과를 즉시 반환한다")
    void returnEmptyResultWhenChannelIdsIsEmpty() {
        LiveStatusResult result = client.fetchLiveStatuses(Set.of());

        assertThat(result.openStreams()).isEmpty();
        assertThat(result.closedChannelIds()).isEmpty();
        assertThat(result.failedChannelIds()).isEmpty();
    }
}
