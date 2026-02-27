package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamServiceTest {

    private StreamService streamService;

    @BeforeEach
    void setUp() {
        streamService = new StreamService();
    }

    @Test
    void 방송_목록을_동기화하면_메모리에_저장되어야_한다() {
        // given
        StreamSyncRequest request = new StreamSyncRequest("ch1", "침착맨", "제목", "thumb.jpg", "소통");
        List<StreamSyncRequest> requests = List.of(request);

        // when
        streamService.syncAll(requests);

        // then
        List<StreamSyncRequest> allStreams = streamService.getAllStreams();
        assertThat(allStreams).hasSize(1);
        assertThat(allStreams.get(0).streamId()).isEqualTo("ch1");
    }

    @Test
    void 같은_채널_ID로_동기화하면_기존_데이터가_업데이트되어야_한다() {
        // given
        StreamSyncRequest oldRequest = new StreamSyncRequest("ch1", "침착맨", "옛날 제목", "old.jpg", "소통");
        StreamSyncRequest newRequest = new StreamSyncRequest("ch1", "침착맨", "새 제목", "new.jpg", "게임");

        streamService.syncAll(List.of(oldRequest));

        // when
        streamService.syncAll(List.of(newRequest));

        // then
        List<StreamSyncRequest> allStreams = streamService.getAllStreams();
        assertThat(allStreams).hasSize(1);
        assertThat(allStreams.get(0).liveTitle()).isEqualTo("새 제목");
        assertThat(allStreams.get(0).categoryName()).isEqualTo("게임");
    }
}
