package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerSimilarityRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSimilarityResponse;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSimilarityQueryServiceTest {

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private JpaStreamerSimilarityRepository similarityRepository;

    private StreamerSimilarityQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new StreamerSimilarityQueryService(
            streamRepository,
            similarityRepository
        );
    }

    @Test
    void 존재하지_않는_스트리머인_경우_예외가_발생한다() {
        String nonExistChannelId = "non_exist_streamer";
        given(streamRepository.findByStreamId(nonExistChannelId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getSimilarities(nonExistChannelId))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 최신_유사도_데이터가_정상인_경우_유사_채널_목록과_상태가_반환된다() {
        String channelId = "ch_main";
        StreamEntity streamer = new StreamEntity(channelId, "메인스트리머");
        LocalDate date = LocalDate.of(2026, 9, 28);

        StreamerSimilarityEntity sim1 = new StreamerSimilarityEntity(
            channelId, SimilarityStatus.NORMAL, "ch_target_1", "스트리머1",
            "https://img.png/1", "Just Chatting", 1, 15.5, 300, 2000, date
        );
        StreamerSimilarityEntity sim2 = new StreamerSimilarityEntity(
            channelId, SimilarityStatus.NORMAL, "ch_target_2", "스트리머2",
            "https://img.png/2", "League of Legends", 2, 8.2, 160, 2100, date
        );

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(streamer));
        given(similarityRepository.findLatestSimilaritiesByStreamId(channelId))
            .willReturn(List.of(sim1, sim2));

        StreamerSimilarityResponse response = queryService.getSimilarities(channelId);

        assertThat(response.streamId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(SimilarityStatus.NORMAL);
        assertThat(response.calculatedDate()).isEqualTo(date);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).rank()).isEqualTo(1);
        assertThat(response.items().get(0).channelId()).isEqualTo("ch_target_1");
        assertThat(response.items().get(0).streamerName()).isEqualTo("스트리머1");
        assertThat(response.items().get(0).similarityPercent()).isEqualTo(15.5);
    }

    @Test
    void 표본_부족_상태인_경우_빈_목록과_INSUFFICIENT_DATA_상태가_반환된다() {
        String channelId = "ch_small";
        StreamEntity streamer = new StreamEntity(channelId, "하꼬스트리머");
        LocalDate date = LocalDate.of(2026, 9, 28);

        StreamerSimilarityEntity insufficient = StreamerSimilarityEntity.emptyState(
            channelId, SimilarityStatus.INSUFFICIENT_DATA, date
        );

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(streamer));
        given(similarityRepository.findLatestSimilaritiesByStreamId(channelId))
            .willReturn(List.of(insufficient));

        StreamerSimilarityResponse response = queryService.getSimilarities(channelId);

        assertThat(response.streamId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(SimilarityStatus.INSUFFICIENT_DATA);
        assertThat(response.calculatedDate()).isEqualTo(date);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void 독립_팬덤_상태인_경우_빈_목록과_INDEPENDENT_FANDOM_상태가_반환된다() {
        String channelId = "ch_island";
        StreamEntity streamer = new StreamEntity(channelId, "독립스트리머");
        LocalDate date = LocalDate.of(2026, 9, 28);

        StreamerSimilarityEntity independent = StreamerSimilarityEntity.emptyState(
            channelId, SimilarityStatus.INDEPENDENT_FANDOM, date
        );

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(streamer));
        given(similarityRepository.findLatestSimilaritiesByStreamId(channelId))
            .willReturn(List.of(independent));

        StreamerSimilarityResponse response = queryService.getSimilarities(channelId);

        assertThat(response.streamId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(SimilarityStatus.INDEPENDENT_FANDOM);
        assertThat(response.calculatedDate()).isEqualTo(date);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void 집계된_유사도_데이터가_전혀_없는_신규_스트리머인_경우_INSUFFICIENT_DATA_상태로_반환된다() {
        String channelId = "ch_brand_new";
        StreamEntity streamer = new StreamEntity(channelId, "신규스트리머");

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(streamer));
        given(similarityRepository.findLatestSimilaritiesByStreamId(channelId))
            .willReturn(Collections.emptyList());

        StreamerSimilarityResponse response = queryService.getSimilarities(channelId);

        assertThat(response.streamId()).isEqualTo(channelId);
        assertThat(response.status()).isEqualTo(SimilarityStatus.INSUFFICIENT_DATA);
        assertThat(response.calculatedDate()).isNull();
        assertThat(response.items()).isEmpty();
    }
}
