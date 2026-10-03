package io.slice.stream.engine.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerDetector;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest {

    private ChatFirepowerDetector detector;
    private StreamTierInfo megaTier;
    private StreamTierInfo regularTier;
    private StreamTierInfo microTier;

    @BeforeEach
    void setUp() {
        detector = new ChatFirepowerDetector();

        // Tier 1: MEGA (대형방) - 180초 윈도우, Z 허들 3.0, noiseFloor 12, 1% 컷 30
        megaTier = StreamTierInfo.builder()
            .streamId("stream-mega")
            .tier(StreamTier.MEGA)
            .minFirepowerCutoff(30L)
            .noiseFloor(12L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(3.0)
            .maskingExclusionTicks(4)
            .build();

        regularTier = StreamTierInfo.builder()
            .streamId("stream-regular")
            .tier(StreamTier.REGULAR)
            .minFirepowerCutoff(10L)
            .noiseFloor(6L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(3.5)
            .maskingExclusionTicks(4)
            .build();

        microTier = StreamTierInfo.builder()
            .streamId("stream-micro")
            .tier(StreamTier.MICRO)
            .minFirepowerCutoff(5L)
            .noiseFloor(5L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(4.0)
            .maskingExclusionTicks(4)
            .build();
    }

    @Test
    void 최소_분석_데이터_모수_30틱_미만이_부족하면_WAITING을_반환한다() {
        // given (29개 데이터만 제공)
        List<Long> deltas = new ArrayList<>(Collections.nCopies(29, 20L));

        // when
        DetectionResult result = detector.detect("stream-regular", deltas, regularTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.WAITING);
    }

    @Test
    void 노이즈_바닥값_미만이면_상대적_폭발이어도_가짜_알람을_방어하고_NORMAL을_반환한다() {
        // given (평소 0개이다가 8개가 터짐. 상대적 폭발이나 MEGA 바닥값인 12에 미달)
        List<Long> deltas = new ArrayList<>(Collections.nCopies(60, 0L));
        deltas.add(8L);

        // when
        DetectionResult result = detector.detect("quiet_room", deltas, megaTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
        assertThat(result.firepower()).isEqualTo(8L);
    }

    @Test
    void 동적_1퍼센트_임계치_미만인_경우_NORMAL을_반환한다() {
        // given (바닥값 12는 넘지만 1% 컷인 30에 미달하는 25)
        List<Long> deltas = new ArrayList<>(Collections.nCopies(60, 2L));
        deltas.add(25L);

        // when
        DetectionResult result = detector.detect("stream-mega", deltas, megaTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
        assertThat(result.firepower()).isEqualTo(25L);
    }

    @Test
    void 메가_체급에서_연쇄_피크가_발생해도_Robust_MAD_기반으로_2차_3차_피크를_모두_감지한다() {
        // given: 80초간 지속되는 3연속 대형 화력 시계열 재현
        // 평소 베이스라인: 12 ~ 15건
        List<Long> deltas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            deltas.add((long) (12 + (i % 4))); // 12, 13, 14, 15 반복 (중앙값 약 13.5, MAD 약 1.5)
        }

        // 1차 절정 발생: 45 -> 65
        deltas.add(45L);
        deltas.add(65L);
        DetectionResult peak1 = detector.detect("stream-mega", deltas, megaTier);
        assertThat(peak1.status()).isEqualTo(ChatFirepowerStatus.PEAK);

        // 2차 폭소 발생 (직후 6초 뒤): 62 (현행 산술평균 엔진에서는 2.22로 탈락했던 지점)
        deltas.add(62L);
        DetectionResult peak2 = detector.detect("stream-mega", deltas, megaTier);
        assertThat(peak2.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(peak2.firepower()).isEqualTo(62L);

        // 지속적 폭소 및 80초 뒤 3차 클라이맥스 (화력 66)
        // 현행 엔진에서는 윈도우 절반이 오염되어 Z-Score 1.43으로 완전 탈락했던 구간!
        for (int i = 0; i < 10; i++) {
            deltas.add(35L); // 연쇄 폭소 지속
        }
        deltas.add(66L); // 최고 클라이맥스
        DetectionResult peak3 = detector.detect("stream-mega", deltas, megaTier);

        // then: Robust MAD는 중앙값 저항력으로 인해 여전히 PEAK를 정확히 포착해야 한다!
        assertThat(peak3.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(peak3.firepower()).isEqualTo(66L);
    }

    @Test
    void 완벽한_정적_상태에서도_MIN_MAD_FLOOR에_의해_DivideByZero_에러_없이_안전하게_처리한다() {
        // given (계속 0개만 나오다가 1% 컷과 바닥값을 넘는 35개 등장)
        List<Long> deltas = new ArrayList<>(Collections.nCopies(60, 0L));
        deltas.add(35L);

        // when
        DetectionResult result = detector.detect("empty_room", deltas, megaTier);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
    }

    @Test
    void 체급별_허들에_따라_정확한_PEAK_판정을_내린다() {
        List<Long> deltas = new ArrayList<>(Collections.nCopies(60, 2L));
        deltas.add(15L);

        DetectionResult result = detector.detect("regular_room", deltas, regularTier);

        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(result.firepower()).isEqualTo(15L);
    }

    @Test
    void 마스킹_제외_틱수가_0일_때도_정상적으로_PEAK를_판정한다() {
        StreamTierInfo unmaskedTier = StreamTierInfo.builder()
            .streamId("stream-unmasked")
            .tier(StreamTier.REGULAR)
            .minFirepowerCutoff(10L)
            .noiseFloor(6L)
            .windowSeconds(180)
            .windowTicks(60)
            .zScoreThreshold(3.5)
            .maskingExclusionTicks(0)
            .build();

        List<Long> deltas = new ArrayList<>(Collections.nCopies(60, 2L));
        deltas.add(15L);

        DetectionResult result = detector.detect("stream-unmasked", deltas, unmaskedTier);

        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
        assertThat(result.firepower()).isEqualTo(15L);
    }
}
