package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @InjectMocks
    private StreamService streamService;

    @Captor
    private ArgumentCaptor<StreamEntity> streamCaptor;

    @Test
    void 방송_목록을_동기화하면_DB에_upsert_되어야_한다() {
        // given
        StreamSyncRequest request = new StreamSyncRequest("ch1", "침착맨", "제목", "thumb.jpg", "소통");

        // when
        streamService.syncAll(List.of(request));

        // then
        then(streamRepository).should().upsertStream(streamCaptor.capture(), any(Instant.class));

        StreamEntity savedEntity = streamCaptor.getValue();
        assertThat(savedEntity.getStreamId()).isEqualTo("ch1");
        assertThat(savedEntity.isLive()).isTrue();
    }

    @Test
    void 배치_내에_중복된_채널_ID가_있으면_최신_데이터로_한번만_upsert_되어야_한다() {
        // given
        StreamSyncRequest oldRequest = new StreamSyncRequest("ch1", "침착맨", "옛날 제목", "old.jpg", "소통");
        StreamSyncRequest newRequest = new StreamSyncRequest("ch1", "침착맨", "새 제목", "new.jpg", "게임");

        // when
        streamService.syncAll(List.of(oldRequest, newRequest));

        // then
        then(streamRepository).should(times(1)).upsertStream(streamCaptor.capture(), any(Instant.class));

        StreamEntity updatedEntity = streamCaptor.getValue();
        assertThat(updatedEntity.getLiveTitle()).isEqualTo("새 제목");
        assertThat(updatedEntity.getCategoryName()).isEqualTo("게임");
    }
}
