package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        entity.heartbeat("침착맨", "제목", "url","게임", 1000);

        given(streamRepository.findActiveStreams(any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList(null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).concurrentUserCount()).isEqualTo(1000);
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.LIVE);
        then(streamRepository).should().findActiveStreams(any(Instant.class));

        // 💡 String, Instant 파라미터 2개 모두 any() 처리
        then(streamRepository).should(never()).searchByStreamerName(anyString(), any(Instant.class));
    }

    @Test
    void 검색어가_있으면_이름으로_방송을_검색한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임", 1000);

        given(streamRepository.searchByStreamerName(eq("침착맨"), any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("침착맨");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).streamerName()).isEqualTo("침착맨");
        assertThat(result.get(0).concurrentUserCount()).isEqualTo(1000);
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.LIVE);

        // 💡 검증(then) 시에도 eq(), any() 매처 적용
        then(streamRepository).should().searchByStreamerName(eq("침착맨"), any(Instant.class));
        then(streamRepository).should(never()).findActiveStreams(any(Instant.class));
    }

    @Test
    void 분석_신호가_있는_방송은_검색_결과에서도_ANALYZING_상태여야_한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임", 1000);

        given(streamRepository.searchByStreamerName(eq("침착맨"), any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of("ch1"));

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("침착맨");

        // then
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.ANALYZING);
    }

    @Test
    void 오프라인_방송은_검색_결과에서_OFFLINE_상태여야_한다() {
        // given
        StreamEntity entity = new StreamEntity("ch1", "침착맨");
        entity.heartbeat("침착맨", "제목", "url", "게임", 1000);
        entity.markOffline(); // 방송 종료 처리

        given(streamRepository.searchByStreamerName(eq("침착맨"), any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("침착맨");

        // then
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.OFFLINE);
    }

    @Test
    void 존재하는_스트림_아이디로_상세_정보를_조회한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = new StreamEntity(streamId, "스트리머");
        entity.heartbeat("스트리머", "라이브 제목", "http://profile.url", "게임", 1000);

        given(streamRepository.findById(streamId))
            .willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId)))
            .willReturn(Set.of(streamId)); // 분석 중인 상태로 설정

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.streamId()).isEqualTo(streamId);
        assertThat(result.streamerName()).isEqualTo("스트리머");
        assertThat(result.concurrentUserCount()).isEqualTo(1000);
        assertThat(result.status()).isEqualTo(StreamStatus.ANALYZING);
        then(streamRepository).should().findById(streamId);
    }

    @Test
    void 최근_신호가_없는_방송은_상세_조회에서도_LIVE_상태여야_한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = new StreamEntity(streamId, "스트리머");
        entity.heartbeat("스트리머", "라이브 제목", "url", "게임", 1000); // 💡 명시적 초기화 추가

        given(streamRepository.findById(streamId))
            .willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId)))
            .willReturn(Set.of()); // 분석 신호 없음

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.status()).isEqualTo(StreamStatus.LIVE);
    }

    @Test
    void 오프라인_상태인_방송을_상세_조회하면_OFFLINE_상태여야_한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = new StreamEntity(streamId, "스트리머");
        entity.heartbeat("스트리머", "라이브 제목", "url", "게임", 1000);
        entity.markOffline(); // 방송 종료 처리

        given(streamRepository.findById(streamId))
            .willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId)))
            .willReturn(Set.of());

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.status()).isEqualTo(StreamStatus.OFFLINE);
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

    @Test
    void isLive가_true여도_lastUpdateAt이_TTL을_지났다면_목록에서_OFFLINE으로_판별한다() {
        // given
        StreamEntity entity = spy(new StreamEntity("zombie1", "좀비스트리머"));
        entity.heartbeat("좀비스트리머", "방종 안하고 튕긴 방송", "url", "게임", 1000);

        given(entity.getLastUpdateAt()).willReturn(Instant.now().minus(10, ChronoUnit.MINUTES));

        given(streamRepository.searchByStreamerName(eq("좀비"), any(Instant.class)))
            .willReturn(List.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(anyCollection()))
            .willReturn(Set.of());

        // when
        List<StreamResponse> result = streamQueryService.getBrowserList("좀비");

        // then
        assertThat(result.get(0).status()).isEqualTo(StreamStatus.OFFLINE);
    }

    @Test
    void 상세조회시_isLive가_true여도_마지막업데이트가_TTL을_지났다면_OFFLINE으로_반환한다() {
        // given
        String streamId = "ch1";
        StreamEntity entity = spy(new StreamEntity(streamId, "좀비스트리머"));
        entity.heartbeat("좀비스트리머", "방종 안하고 튕긴 방송", "url", "게임", 1000);

        given(entity.getLastUpdateAt()).willReturn(Instant.now().minus(10, ChronoUnit.MINUTES));
        given(streamRepository.findById(streamId)).willReturn(Optional.of(entity));
        given(analysisRepository.findChannelsWithRecentSignals(Set.of(streamId))).willReturn(Set.of());

        // when
        StreamResponse result = streamQueryService.getStreamInfo(streamId);

        // then
        assertThat(result.status()).isEqualTo(StreamStatus.OFFLINE);
    }
}
