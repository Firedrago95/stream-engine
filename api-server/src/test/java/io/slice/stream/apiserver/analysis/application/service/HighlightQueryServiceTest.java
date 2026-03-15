package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightQueryServiceTest {

    @Mock
    private JpaHighlightEventRepository repository;

    @InjectMocks
    private HighlightQueryService highlightQueryService;

    @Test
    void 특정_날짜의_하이라이트_목록을_조회하여_DTO로_변환한다() {
        // given
        String streamId = "stream-123";
        LocalDate date = LocalDate.of(2026, 3, 4);

        Instant start = Instant.parse("2026-03-04T10:00:00Z");
        Instant end = start.plusSeconds(60);

        // VOD 오프셋 데이터 설정 (시작점: 1시간 지점, 종료점: 1시간 1분 지점)
        long startOffset = 3600000L;
        long endOffset = 3660000L;

        HighlightEventEntity entity = new HighlightEventEntity(
            streamId,
            start,
            startOffset,
            start, // 최초 피크 시간
            startOffset, // 최초 피크 오프셋
            500L
        );

        // [수정] finish 메서드에 종료 오프셋 추가
        entity.finish(end, endOffset);

        given(repository.findAllByStreamIdAndDateRange(eq(streamId), any(Instant.class), any(Instant.class)))
            .willReturn(List.of(entity));

        // when
        List<HighlightResponse> results = highlightQueryService.getHighlightsByDate(streamId, date);

        // then
        assertThat(results).hasSize(1);
        HighlightResponse response = results.get(0);

        assertThat(response.durationSeconds()).isEqualTo(60L);
        assertThat(response.startTimeOffset()).isEqualTo(startOffset);
        assertThat(response.endTimeOffset()).isEqualTo(endOffset);
    }

    @Test
    void 날짜_조회_시_해당_날짜의_00시부터_익일_00시_미만까지의_범위를_사용한다() {
        // given
        String streamId = "stream-123";
        LocalDate date = LocalDate.of(2026, 3, 4);

        // when
        highlightQueryService.getHighlightsByDate(streamId, date);

        // then
        verify(repository).findAllByStreamIdAndDateRange(
            eq(streamId),
            any(Instant.class),
            any(Instant.class)
        );
    }
}
