package io.slice.stream.engine.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerDetector;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest {

    private ChatFirepowerDetector detector;
    private StreamTierInfo tierInfo;

    @BeforeEach
    void setUp() {
        detector = new ChatFirepowerDetector();
        tierInfo = StreamTierInfo.builder()
            .streamId("stream-test")
            .noiseFloor(15L)
            .build();
    }

    @Test
    void 분석_데이터가_없으면_WAITING을_반환한다() {
        DetectionResult resultNull = detector.detect("stream-test", null, tierInfo);
        DetectionResult resultEmpty = detector.detect("stream-test", Collections.emptyList(), tierInfo);

        assertThat(resultNull.status()).isEqualTo(ChatFirepowerStatus.WAITING);
        assertThat(resultEmpty.status()).isEqualTo(ChatFirepowerStatus.WAITING);
    }

    @Test
    void 노이즈_바닥값_미만이면_NORMAL을_반환한다() {
        List<Long> deltas = List.of(5L, 8L, 14L);

        DetectionResult result = detector.detect("stream-test", deltas, tierInfo);

        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
        assertThat(result.firepower()).isEqualTo(14L);
    }

    @Test
    void 노이즈_바닥값_이상이면_PEAK를_반환한다() {
        List<Long> deltas = List.of(5L, 8L, 15L);

        DetectionResult result = detector.detect("stream-test", deltas, tierInfo);

        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(result.firepower()).isEqualTo(15L);
    }

    @Test
    void 연속_피크_및_여진이_발생해도_바닥값을_넘으면_모두_정상_감지한다() {
        List<Long> peak1Deltas = List.of(5L, 8L, 50L);
        DetectionResult peak1 = detector.detect("stream-test", peak1Deltas, tierInfo);
        assertThat(peak1.status()).isEqualTo(ChatFirepowerStatus.PEAK);

        List<Long> peak2Deltas = List.of(8L, 50L, 45L);
        DetectionResult peak2 = detector.detect("stream-test", peak2Deltas, tierInfo);
        assertThat(peak2.status()).isEqualTo(ChatFirepowerStatus.PEAK);

        List<Long> peak3Deltas = List.of(50L, 45L, 20L);
        DetectionResult peak3 = detector.detect("stream-test", peak3Deltas, tierInfo);
        assertThat(peak3.status()).isEqualTo(ChatFirepowerStatus.PEAK);
    }
}
