package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    @Mock
    StreamRepository streamRepository;

    @Mock
    AnalysisRepository analysisRepository;

    @InjectMocks
    StreamQueryService streamQueryService;

    @Test
    void 검색어가_없으면_활성화된_방송_목록을_조회한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임");

        given(streamRepository.findActiveStreams(any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList(null);

        // then
        assertThat(result).hasSize(1);
        then(streamRepository).should().findActiveStreams(any(Instant.class));
        then(streamRepository).should(never()).findByStreamerNameContainingIgnoreCase(anyString());
    }

    @Test
    void 검색어가_있으면_이름으로_방송을_검색한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임");

        given(streamRepository.findByStreamerNameContainingIgnoreCase("침착맨"))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("침착맨");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamerName()).isEqualTo("침착맨");
        then(streamRepository).should().findByStreamerNameContainingIgnoreCase("침착맨");
        then(streamRepository).should(never()).findActiveStreams(any(Instant.class));
    }

    @Test
    void 분석_신호가_있는_방송은_검색_결과에서도_ANALYZING_상태여야_한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임");

        given(streamRepository.findByStreamerNameContainingIgnoreCase("침착맨"))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of("ch1"));

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("침착맨");

        // then
        assertThat(result.get(0).status()).isEqualTo("ANALYZING");
    }

    @Test
    void 존재하는_스트림_아이디로_상세_정보를_조회한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = new StreamEntity(streamId, "스트리머");
        entity.heartbeat("스트리머", "라이브 제목", "http://profile.url", "게임");

        given(streamRepository.findById(streamId))
            .willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId)))
            .willReturn(Set.of(streamId)); // 분석 중인 상태로 설정

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.streamId()).isEqualTo(streamId);
        assertThat(result.streamerName()).isEqualTo("스트리머");
        assertThat(result.status()).isEqualTo("ANALYZING");
        then(streamRepository).should().findById(streamId);
    }

    @Test
    void 최근_신호가_없는_방송은_상세_조회에서도_LIVE_상태여야_한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = new StreamEntity(streamId, "스트리머");

        given(streamRepository.findById(streamId))
            .willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId)))
            .willReturn(Set.of()); // 분석 신호 없음

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.status()).isEqualTo("LIVE");
    }

    @Test
    void 존재하지_않는_스트림_아이디로_조회하면_예외가_발생한다() {
        // given
        String streamId = "non-existent";
        given(streamRepository.findById(streamId))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> streamQueryService.getStreamInfo(streamId))
            .isInstanceOf(BusinessException.class);
    }
}
