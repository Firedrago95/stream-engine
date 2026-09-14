package io.slice.stream.apiserver.streamer.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.streamer.domain.model.DailySplitSegment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SessionMidnightSplitterTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private SessionMidnightSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new SessionMidnightSplitter(KST);
    }

    @Test
    void 당일_내_시작되고_종료된_방송은_단일_세그먼트로_분할된다() {
        Instant startedAt = Instant.parse("2026-09-13T05:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T09:00:00Z");

        List<DailySplitSegment> segments = splitter.split(
            startedAt, endedAt, 5000, 3000, "당일 방송", "Talk"
        );

        assertThat(segments).hasSize(1);
        DailySplitSegment segment = segments.get(0);
        assertThat(segment.date()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(segment.durationSeconds()).isEqualTo(14400L);
        assertThat(segment.peakViewers()).isEqualTo(5000);
        assertThat(segment.avgViewers()).isEqualTo(3000);
        assertThat(segment.title()).isEqualTo("당일 방송");
        assertThat(segment.category()).isEqualTo("Talk");
    }

    @Test
    void 자정을_넘겨_익일_새벽에_종료된_방송은_두_날짜로_정확히_분할된다() {
        Instant startedAt = Instant.parse("2026-09-13T11:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T17:00:00Z");

        List<DailySplitSegment> segments = splitter.split(
            startedAt, endedAt, 8000, 4000, "야간 방송", "League of Legends"
        );

        assertThat(segments).hasSize(2);

        DailySplitSegment day1 = segments.get(0);
        assertThat(day1.date()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(day1.durationSeconds()).isEqualTo(14400L);

        DailySplitSegment day2 = segments.get(1);
        assertThat(day2.date()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(day2.durationSeconds()).isEqualTo(7200L);
    }

    @Test
    void 사흘에_걸쳐_진행된_초장기_방송은_3개의_일자별_세그먼트로_분할된다() {
        Instant startedAt = Instant.parse("2026-09-13T13:00:00Z");
        Instant endedAt = Instant.parse("2026-09-15T02:00:00Z");

        List<DailySplitSegment> segments = splitter.split(
            startedAt, endedAt, 10000, 6000, "마라톤 방송", " 종합 게임"
        );

        assertThat(segments).hasSize(3);

        assertThat(segments.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(segments.get(0).durationSeconds()).isEqualTo(7200L);

        assertThat(segments.get(1).date()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(segments.get(1).durationSeconds()).isEqualTo(86400L);

        assertThat(segments.get(2).date()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(segments.get(2).durationSeconds()).isEqualTo(39600L);
    }

    @Test
    void 시작_시각과_종료_시각이_역전된_경우_빈_리스트를_반환한다() {
        Instant startedAt = Instant.parse("2026-09-13T15:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T10:00:00Z");

        List<DailySplitSegment> segments = splitter.split(
            startedAt, endedAt, 1000, 500, "비정상", "기타"
        );

        assertThat(segments).isEmpty();
    }
}
