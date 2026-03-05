package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
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
    private ArgumentCaptor<List<StreamEntity>> streamsCaptor;

    @Test
    void 신규_방송_목록을_동기화하면_DB에_저장되어야_한다() {
        // given
        StreamSyncRequest request = new StreamSyncRequest("ch1", "침착맨", "제목", "thumb.jpg", "소통");

        given(streamRepository.findAllByStreamIdIn(List.of("ch1"))).willReturn(List.of());

        // when
        streamService.syncAll(List.of(request));

        // then
        then(streamRepository).should().saveAll(streamsCaptor.capture());

        List<StreamEntity> savedStreams = streamsCaptor.getValue();
        assertThat(savedStreams).hasSize(1);

        StreamEntity savedEntity = savedStreams.get(0);
        assertThat(savedEntity.getStreamId()).isEqualTo("ch1");
        assertThat(savedEntity.isLive()).isTrue();
        assertThat(savedEntity.getLastUpdateAt()).isNotNull();
    }

    @Test
    void 같은_채널_ID로_동기화하면_기존_데이터가_업데이트되어야_한다() {
        // given
        StreamEntity existingEntity = new StreamEntity("ch1", "침착맨");
        existingEntity.heartbeat("침착맨", "옛날 제목", "old.jpg", "소통");

        StreamSyncRequest newRequest = new StreamSyncRequest("ch1", "침착맨", "새 제목", "new.jpg", "게임");

        given(streamRepository.findAllByStreamIdIn(List.of("ch1"))).willReturn(List.of(existingEntity));

        // when
        streamService.syncAll(List.of(newRequest));

        // then
        then(streamRepository).should().saveAll(streamsCaptor.capture());

        List<StreamEntity> savedStreams = streamsCaptor.getValue();
        assertThat(savedStreams).hasSize(1);

        StreamEntity updatedEntity = savedStreams.get(0);
        assertThat(updatedEntity.getLiveTitle()).isEqualTo("새 제목");
        assertThat(updatedEntity.getCategoryName()).isEqualTo("게임");
    }
}
