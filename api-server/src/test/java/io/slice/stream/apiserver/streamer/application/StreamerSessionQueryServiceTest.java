package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSessionQueryServiceTest {

    private FakeStreamSessionRepository sessionRepository;
    private StreamerSessionQueryService sessionQueryService;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeStreamSessionRepository();
        sessionQueryService = new StreamerSessionQueryService(sessionRepository);
    }

    @Test
    void 세션_목록을_페이징하여_전적_DTO로_정상_반환한다() {
        String channelId = "ch_sess_test";
        Instant now = Instant.now();

        StreamSessionEntity session = new StreamSessionEntity(
            channelId, "sess_100", "어제의 방송", "Talk", now.minus(5, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(session, "endedAt", now.minus(2, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(session, "peakViewers", 4500);
        ReflectionTestUtils.setField(session, "averageViewerCount", 3200);
        ReflectionTestUtils.setField(session, "sessionFollowerGrowth", 80);
        ReflectionTestUtils.setField(session, "subscriberChatRatio", 42.5);

        sessionRepository.addSession(session);

        StreamerSessionHistoryResponse response = sessionQueryService.getSessionHistory(channelId, 0, 10);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.hasNext()).isFalse();

        assertThat(response.sessions()).hasSize(1);
        var item = response.sessions().get(0);
        assertThat(item.sessionId()).isEqualTo("sess_100");
        assertThat(item.title()).isEqualTo("어제의 방송");
        assertThat(item.categoryName()).isEqualTo("Talk");
        assertThat(item.peakViewers()).isEqualTo(4500);
        assertThat(item.avgViewers()).isEqualTo(3200);
        assertThat(item.followerGrowth()).isEqualTo(80);
        assertThat(item.subscriberChatRatio()).isEqualTo(42.5);
        assertThat(item.durationSeconds()).isEqualTo(10800L);
        assertThat(item.paidPromotion()).isFalse();
    }

    @Test
    void 유료_프로모션_필터_조회시_paidPromotion이_참인_세션만_조회한다() {
        String channelId = "ch_sess_test";
        Instant now = Instant.now();

        StreamSessionEntity normalSession = new StreamSessionEntity(
            channelId, "sess_normal", "일반 방송", "Talk", now.minus(4, ChronoUnit.HOURS), false
        );
        ReflectionTestUtils.setField(normalSession, "endedAt", now.minus(3, ChronoUnit.HOURS));

        StreamSessionEntity paidSession = new StreamSessionEntity(
            channelId, "sess_paid", "광고 방송", "Game", now.minus(2, ChronoUnit.HOURS), true
        );
        ReflectionTestUtils.setField(paidSession, "endedAt", now.minus(1, ChronoUnit.HOURS));

        sessionRepository.addSession(normalSession);
        sessionRepository.addSession(paidSession);

        StreamerSessionHistoryResponse response = sessionQueryService.getSessionHistory(channelId, 0, 10, true);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.sessions()).hasSize(1);
        assertThat(response.sessions().get(0).sessionId()).isEqualTo("sess_paid");
        assertThat(response.sessions().get(0).paidPromotion()).isTrue();
    }

    @Test
    void 오분_미만의_종료된_단기_노이즈_세션은_목록에서_제외되고_오분_이상_세션과_라이브_세션만_조회된다() {
        String channelId = "ch_noise_test";
        Instant now = Instant.now();

        // 1분(92초)짜리 튕김 세션 -> 제외 대상
        StreamSessionEntity shortSession1 = new StreamSessionEntity(
            channelId, "sess_92s", "1분 방송", "Talk", now.minus(5, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(shortSession1, "endedAt", now.minus(5, ChronoUnit.HOURS).plusSeconds(92));

        // 4분 59초(299초)짜리 세션 -> 제외 대상
        StreamSessionEntity shortSession2 = new StreamSessionEntity(
            channelId, "sess_299s", "약 5분 미만 방송", "Game", now.minus(4, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(shortSession2, "endedAt", now.minus(4, ChronoUnit.HOURS).plusSeconds(299));

        // 정확히 5분(300초) 방송 -> 포함 대상
        StreamSessionEntity exact5mSession = new StreamSessionEntity(
            channelId, "sess_300s", "정확히 5분 방송", "Game", now.minus(3, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(exact5mSession, "endedAt", now.minus(3, ChronoUnit.HOURS).plusSeconds(300));

        // 2시간 본방송 -> 포함 대상
        StreamSessionEntity longSession = new StreamSessionEntity(
            channelId, "sess_long", "2시간 본방송", "Game", now.minus(2, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(longSession, "endedAt", now);

        // 방금 켠 1분짜리 라이브 세션 (endedAt == null) -> 라이브이므로 포함 대상
        StreamSessionEntity liveSession = new StreamSessionEntity(
            channelId, "sess_live", "현재 라이브 방송", "Talk", now.minusSeconds(60)
        );

        sessionRepository.addSession(shortSession1);
        sessionRepository.addSession(shortSession2);
        sessionRepository.addSession(exact5mSession);
        sessionRepository.addSession(longSession);
        sessionRepository.addSession(liveSession);

        StreamerSessionHistoryResponse response = sessionQueryService.getSessionHistory(channelId, 0, 10);

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.sessions()).hasSize(3);
        List<String> resultSessionIds = response.sessions().stream()
            .map(StreamerSessionHistoryResponse.StreamerSessionItemDto::sessionId)
            .toList();

        assertThat(resultSessionIds).containsExactly("sess_live", "sess_long", "sess_300s");
        assertThat(resultSessionIds).doesNotContain("sess_92s", "sess_299s");
    }
}
