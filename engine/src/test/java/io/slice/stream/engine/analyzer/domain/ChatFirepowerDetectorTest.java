package io.slice.stream.engine.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerDetector;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest {

    private ChatFirepowerDetector detector;
    private StreamTierInfo groupATier;
    private StreamTierInfo groupBTier;

    @BeforeEach
    void setUp() {
        detector = new ChatFirepowerDetector();

        // 테스트용 체급 파라미터 세팅 (대기업)
        groupATier = StreamTierInfo.builder()
            .streamId("stream-A")
            .tier(StreamTier.GROUP_A)
            .minFirepowerCutoff(30L)      // 1% 컷 30개
            .maskingExclusionTicks(4)     // 마스킹 4틱
            .zScoreThreshold(3.5)
            .build();

        // 테스트용 체급 파라미터 세팅 (성장형)
        groupBTier = StreamTierInfo.builder()
            .streamId("stream-B")
            .tier(StreamTier.GROUP_B)
            .minFirepowerCutoff(10L)      // 1% 컷 10개
            .maskingExclusionTicks(4)
            .zScoreThreshold(4.5)
            .build();
    }

    @Test
    void 최소_데이터_모수가_부족하면_WAITING을_반환한다() {
        // given (9개 데이터만 제공)
        List<Long> deltas = List.of(1L, 2L, 1L, 2L, 1L, 2L, 1L, 2L, 1L);

        // when
        DetectionResult result = detector.detect("stream-B", deltas, groupBTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.WAITING);
    }

    @Test
    void 최소_화력_임계치보다_낮으면_상대적_폭발이어도_NORMAL을_반환한다() {
        // given (평소 1개, 마지막에 5개가 터졌으나 Group B의 1%컷인 10개에 미달)
        List<Long> deltas = generateDeltas(1L, 15);
        deltas.add(5L);

        // when
        DetectionResult result = detector.detect("quiet_room", deltas, groupBTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
        assertThat(result.firepower()).isEqualTo(5L);
    }

    @Test
    void 마스킹_배제_적용시_최근_화력이_과거_평균을_오염시키지_않고_PEAK를_잡아낸다() {
        // given
        List<Long> deltas = generateDeltas(2L, 20); // 잔잔한 평소 채팅

        // 최근 4틱 동안 서서히 화력이 증가하는 마스킹 유발 상황 (10 -> 15 -> 20 -> 대폭발 50)
        deltas.add(10L);
        deltas.add(15L);
        deltas.add(20L);
        deltas.add(50L); // 현재 Delta (Group A 1%컷인 30 넘음)

        // when
        DetectionResult result = detector.detect("burst_room", deltas, groupATier);

        // then (앞선 10, 15, 20이 배제되었으므로 Z-Score가 높게 나와 PEAK 판정 성공)
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(result.firepower()).isEqualTo(50L);
    }

    @Test
    void 완벽한_정적_상태에서도_DivideByZero_에러없이_안전하게_처리한다() {
        // given (계속 0개만 나오다가 1% 컷을 넘는 15개 등장)
        List<Long> deltas = generateDeltas(0L, 20);
        deltas.add(15L);

        // when
        DetectionResult result = detector.detect("empty_room", deltas, groupBTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
    }

    private List<Long> generateDeltas(long value, int count) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(value);
        return list;
    }
}
