package io.slice.stream.engine.ingestion.infrastructure.apiServer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.core.model.StreamTarget;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSyncRequestTest {

    @Test
    void StreamTarget으로부터_StreamSyncRequest_생성시_startedAt이_정상_매핑된다() {
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        StreamTarget target = new StreamTarget(
            "ch1",
            "스트리머",
            "chat1",
            12345L,
            "방송제목",
            500,
            "image.png",
            "종합게임",
            startedAt
        );

        StreamSyncRequest request = StreamSyncRequest.from(target);

        assertThat(request.streamId()).isEqualTo("ch1");
        assertThat(request.liveId()).isEqualTo("12345");
        assertThat(request.streamerName()).isEqualTo("스트리머");
        assertThat(request.liveTitle()).isEqualTo("방송제목");
        assertThat(request.profileImageUrl()).isEqualTo("image.png");
        assertThat(request.concurrentUserCount()).isEqualTo(500);
        assertThat(request.categoryName()).isEqualTo("종합게임");
        assertThat(request.startedAt()).isEqualTo(startedAt);
    }
}
