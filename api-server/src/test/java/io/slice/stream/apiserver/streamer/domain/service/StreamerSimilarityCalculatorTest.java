package io.slice.stream.apiserver.streamer.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator.CalculationResult;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator.SimilarityMatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSimilarityCalculatorTest {

    private StreamerSimilarityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StreamerSimilarityCalculator();
    }

    private Set<Long> generateChatters(long startId, int count) {
        Set<Long> set = new HashSet<>(count);
        for (long i = startId; i < startId + count; i++) {
            set.add(i);
        }
        return set;
    }

    @Test
    void 고유_채팅자_수가_100명_미만인_경우_INSUFFICIENT_DATA_상태를_반환한다() {
        Map<String, Set<Long>> allChatters = new HashMap<>();
        allChatters.put("streamerA", generateChatters(1L, 99)); // 표본 99명 (< 100)
        allChatters.put("streamerB", generateChatters(1L, 500));

        CalculationResult result = calculator.calculate("streamerA", allChatters);

        assertAll(
            () -> assertThat(result.status()).isEqualTo(SimilarityStatus.INSUFFICIENT_DATA),
            () -> assertThat(result.matches()).isEmpty()
        );
    }

    @Test
    void 모든_스트리머와의_최고_유사도가_3퍼센트_미만인_경우_INDEPENDENT_FANDOM_상태를_반환한다() {
        Map<String, Set<Long>> allChatters = new HashMap<>();
        // streamerA: 1,000명 (ID 1 ~ 1,000)
        allChatters.put("streamerA", generateChatters(1L, 1000));
        // streamerB: 1,000명 (ID 991 ~ 1,990) -> 공통 10명 -> 유사도 10 / 1990 = 0.5% (< 3%)
        allChatters.put("streamerB", generateChatters(991L, 1000));

        CalculationResult result = calculator.calculate("streamerA", allChatters);

        assertAll(
            () -> assertThat(result.status()).isEqualTo(SimilarityStatus.INDEPENDENT_FANDOM),
            () -> assertThat(result.matches()).isEmpty()
        );
    }

    @Test
    void 정상_케이스에서_유사도가_높은_상위_3명의_스트리머를_순위대로_추출한다() {
        Map<String, Set<Long>> allChatters = new HashMap<>();
        // streamerA: 1,000명 (ID: 1 ~ 1,000)
        allChatters.put("streamerA", generateChatters(1L, 1000));

        // streamerB: 공통 400명 (1~400), 비공통 600명 (1001~1600) -> 합집합 1,600명 -> 유사도 400/1600 = 25.0%
        allChatters.put("streamerB", generateChatters(1L, 400));
        allChatters.get("streamerB").addAll(generateChatters(1001L, 600));

        // streamerC: 공통 200명 (1~200), 비공통 800명 (2001~2800) -> 합집합 1,800명 -> 유사도 200/1800 = 11.11%
        allChatters.put("streamerC", generateChatters(1L, 200));
        allChatters.get("streamerC").addAll(generateChatters(2001L, 800));

        // streamerD: 공통 100명 (1~100), 비공통 900명 (3001~3900) -> 합집합 1,900명 -> 유사도 100/1900 = 5.26%
        allChatters.put("streamerD", generateChatters(1L, 100));
        allChatters.get("streamerD").addAll(generateChatters(3001L, 900));

        // streamerE: 공통 70명 (1~70), 비공통 930명 (4001~4930) -> 합집합 1,930명 -> 유사도 70/1930 = 3.63%
        allChatters.put("streamerE", generateChatters(1L, 70));
        allChatters.get("streamerE").addAll(generateChatters(4001L, 930));

        CalculationResult result = calculator.calculate("streamerA", allChatters);

        assertAll(
            () -> assertThat(result.status()).isEqualTo(SimilarityStatus.NORMAL),
            () -> assertThat(result.matches()).hasSize(3),
            () -> assertThat(result.matches().get(0).targetStreamId()).isEqualTo("streamerB"),
            () -> assertThat(result.matches().get(0).similarityPercent()).isEqualTo(25.0),
            () -> assertThat(result.matches().get(1).targetStreamId()).isEqualTo("streamerC"),
            () -> assertThat(result.matches().get(2).targetStreamId()).isEqualTo("streamerD")
        );
    }

    @Test
    void 자기_자신은_비교_대상에서_제외된다() {
        Map<String, Set<Long>> allChatters = new HashMap<>();
        allChatters.put("streamerA", generateChatters(1L, 500));
        allChatters.put("streamerB", generateChatters(1L, 500));

        CalculationResult result = calculator.calculate("streamerA", allChatters);

        assertThat(result.matches())
            .noneMatch(match -> match.targetStreamId().equals("streamerA"));
    }
}
