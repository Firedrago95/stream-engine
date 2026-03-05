package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamQueryServiceTest {

    // 💡 StreamService 대신 StreamRepository를 Mocking 합니다 (CQRS 반영)
    @Mock
    StreamRepository streamRepository;

    @Mock
    AnalysisRepository analysisRepository;

    @InjectMocks
    StreamQueryService streamQueryService;

    @Test
    void 분석_신호가_있는_방송은_ANALYZING_상태여야_한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임"); // 세부 정보 세팅

        given(streamRepository.findActiveStreams(any(Instant.class)))
            .willReturn(List.of(entity));

        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of("ch1"));

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("ANALYZING");
        assertThat(result.get(0).streamId()).isEqualTo("ch1");
    }

    @Test
    void 신호가_없는_방송은_LIVE_상태여야_한다() {
        // given
        StreamEntity entity = new StreamEntity("ch2", "주호민");
        entity.heartbeat("주호민", "제목", "url", "소통");

        given(streamRepository.findActiveStreams(any(Instant.class)))
            .willReturn(List.of(entity));

        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("LIVE");
        assertThat(result.get(0).streamId()).isEqualTo("ch2");
    }
}
