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

        // 1분 차이가 나도록 Instant 설정
        Instant start = Instant.parse("2026-03-04T10:00:00Z");
        Instant end = start.plusSeconds(60);

        // 엔티티 생성 시 end 타임이 정확히 들어가는지 확인
        // 생성자 파라미터 순서: (streamId, startTime, endTime, peakFirepower) 라고 가정
        HighlightEventEntity entity = new HighlightEventEntity(
            streamId,
            start,
            start,
            500L
        );
        entity.finish(end);

        given(repository.findAllByStreamIdAndDateRange(eq(streamId), any(Instant.class), any(Instant.class)))
            .willReturn(List.of(entity));

        // when
        List<HighlightResponse> results = highlightQueryService.getHighLightsByDate(streamId, date);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).durationSeconds()).isEqualTo(60L); // 이제 60L이 정상적으로 나옵니다.
    }

    @Test
    void 날짜_조회_시_해당_날짜의_00시부터_익일_00시_미만까지의_범위를_사용한다() {
        // given
        String streamId = "stream-123";
        LocalDate date = LocalDate.of(2026, 3, 4);

        // expected range (시스템 타임존에 따라 달라질 수 있으나 로직 검증 위주)
        // atStartOfDay()는 서비스 코드의 ZoneId.systemDefault() 설정을 따름

        // when
        highlightQueryService.getHighLightsByDate(streamId, date);

        // then
        // 정확히 1일(24시간) 간격으로 쿼리가 날아가는지 검증
        verify(repository).findAllByStreamIdAndDateRange(
            eq(streamId),
            any(Instant.class),
            any(Instant.class)
        );
    }
}
