package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSessionQueryServiceTest {

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    private StreamerSessionQueryService sessionQueryService;

    @BeforeEach
    void setUp() {
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

        PageImpl<StreamSessionEntity> page = new PageImpl<>(
            List.of(session),
            PageRequest.of(0, 10),
            1
        );

        given(sessionRepository.findByStreamIdOrderByStartedAtDesc(eq(channelId), any()))
            .willReturn(page);

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
    }
}
