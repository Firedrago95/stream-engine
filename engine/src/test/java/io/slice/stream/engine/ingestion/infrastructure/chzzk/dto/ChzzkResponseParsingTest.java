package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkChannelResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChzzkResponseParsingTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("치지직 v3 live-detail 응답 JSON을 정상적으로 역직렬화하고 accumulateCount를 파싱한다")
    void deserializeV3LiveDetailResponse() throws Exception {
        String json = """
            {
              "code": 200,
              "message": null,
              "content": {
                "liveId": 21154522,
                "liveTitle": "종조이 우당탕탕 이야기",
                "status": "CLOSE",
                "liveImageUrl": "https://thumb.com/image_{type}.jpg",
                "defaultThumbnailImageUrl": null,
                "concurrentUserCount": 4557,
                "accumulateCount": 215970,
                "openDate": "2026-09-17 17:05:38",
                "closeDate": "2026-09-18 03:52:55",
                "adult": false,
                "paidPromotion": true,
                "chatChannelId": "N2kWtN",
                "categoryType": "GAME",
                "liveCategory": "Grand_Theft_Auto_V",
                "liveCategoryValue": "Grand Theft Auto V",
                "channel": {
                  "channelId": "a67b328bcc8eea4451ccfa754bc19ae1",
                  "channelName": "종조이",
                  "channelImageUrl": "https://thumb.com/profile.png"
                }
              }
            }
            """;

        ChzzkLiveDetailResponse response = objectMapper.readValue(json, ChzzkLiveDetailResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotNull();
        assertThat(response.content().liveId()).isEqualTo(21154522L);
        assertThat(response.content().status()).isEqualTo("CLOSE");
        assertThat(response.content().chatChannelId()).isEqualTo("N2kWtN");
        assertThat(response.content().accumulateCount()).isEqualTo(215970);
        assertThat(response.content().concurrentUserCount()).isEqualTo(4557);
        assertThat(response.content().liveTitle()).isEqualTo("종조이 우당탕탕 이야기");
        assertThat(response.content().paidPromotion()).isTrue();
        assertThat(response.content().channel().channelId()).isEqualTo("a67b328bcc8eea4451ccfa754bc19ae1");
        assertThat(response.content().channel().channelName()).isEqualTo("종조이");
    }

    @Test
    @DisplayName("치지직 v2 live-status 경량 폴링 응답 JSON을 정상적으로 역직렬화하고 chatChannelId를 파싱한다")
    void deserializeV2LiveStatusResponse() throws Exception {
        String json = """
            {
              "code": 200,
              "message": null,
              "content": {
                "liveTitle": "통통꼬리 용가리! 사키하네 후야 3D 데뷔!",
                "status": "OPEN",
                "concurrentUserCount": 19995,
                "accumulateCount": 0,
                "paidPromotion": false,
                "adult": false,
                "krOnlyViewing": false,
                "openDate": "2026-09-18 17:57:33",
                "closeDate": null,
                "clipActive": false,
                "chatChannelId": "N2kh2R",
                "tags": ["스텔라이브", "에버리스"],
                "categoryType": "ETC",
                "liveCategory": "talk",
                "liveCategoryValue": "talk",
                "channelId": "36ddb9bb4f17593b60f1b63cec86611d"
              }
            }
            """;

        ChzzkLiveStatusResponse response = objectMapper.readValue(json, ChzzkLiveStatusResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotNull();
        assertThat(response.content().status()).isEqualTo("OPEN");
        assertThat(response.content().chatChannelId()).isEqualTo("N2kh2R");
        assertThat(response.content().liveTitle()).isEqualTo("통통꼬리 용가리! 사키하네 후야 3D 데뷔!");
        assertThat(response.content().concurrentUserCount()).isEqualTo(19995);
        assertThat(response.content().accumulateCount()).isEqualTo(0);
        assertThat(response.content().channelId()).isEqualTo("36ddb9bb4f17593b60f1b63cec86611d");
        assertThat(response.content().openDate()).isNotNull();
    }

    @Test
    @DisplayName("치지직 v1 channels 응답 JSON을 정상적으로 역직렬화하고 followerCount를 파싱한다")
    void deserializeChannelResponse() throws Exception {
        String json = """
            {
              "code": 200,
              "message": null,
              "content": {
                "channelId": "36ddb9bb4f17593b60f1b63cec86611d",
                "channelName": "사키하네 후야",
                "channelImageUrl": "https://thumb.com/profile.png",
                "verifiedMark": true,
                "verifiedMarkType": null,
                "officialChannel": false,
                "channelType": "STREAMING",
                "channelDescription": "",
                "followerCount": 165484,
                "openLive": false,
                "subscriptionAvailability": true
              }
            }
            """;

        ChzzkChannelResponse response =
            objectMapper.readValue(json, ChzzkChannelResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotNull();
        assertThat(response.content().channelId()).isEqualTo("36ddb9bb4f17593b60f1b63cec86611d");
        assertThat(response.content().channelName()).isEqualTo("사키하네 후야");
        assertThat(response.content().followerCount()).isEqualTo(165484);
        assertThat(response.content().verifiedMark()).isTrue();
    }

    @Test
    @DisplayName("치지직 TopLive 응답 JSON에 paidPromotion 필드가 누락되어도 기본값 false로 안전하게 역직렬화된다")
    void deserializeTopLiveResponseWithoutPaidPromotion() throws Exception {
        String json = """
            {
              "code": 200,
              "message": null,
              "content": {
                "size": 1,
                "page": { "next": null },
                "data": [
                  {
                    "liveId": 12345,
                    "liveTitle": "일반 방송",
                    "liveImageUrl": "https://thumb.com/img.jpg",
                    "liveCategoryValue": "talk",
                    "chatChannelId": "chat123",
                    "concurrentUserCount": 500,
                    "adult": false,
                    "openDate": "2026-09-26 12:00:00",
                    "channel": {
                      "channelId": "ch_1",
                      "channelName": "스트리머1",
                      "channelImageUrl": "https://thumb.com/ch.png"
                    }
                  }
                ]
              }
            }
            """;

        ChzzkLiveResponse response = objectMapper.readValue(json, ChzzkLiveResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.content().data()).hasSize(1);
        assertThat(response.content().data().get(0).paidPromotion()).isFalse();
    }

    @Test
    @DisplayName("치지직 LiveDetail 응답 JSON에 paidPromotion 필드가 누락되어도 기본값 false로 안전하게 역직렬화된다")
    void deserializeLiveDetailResponseWithoutPaidPromotion() throws Exception {
        String json = """
            {
              "code": 200,
              "message": null,
              "content": {
                "liveId": 12345,
                "liveTitle": "일반 방송 상세",
                "status": "OPEN",
                "concurrentUserCount": 500,
                "accumulateCount": 1000,
                "openDate": "2026-09-26 12:00:00",
                "adult": false,
                "channel": {
                  "channelId": "ch_1",
                  "channelName": "스트리머1",
                  "channelImageUrl": "https://thumb.com/ch.png"
                }
              }
            }
            """;

        ChzzkLiveDetailResponse response = objectMapper.readValue(json, ChzzkLiveDetailResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.content().paidPromotion()).isFalse();
    }
}
