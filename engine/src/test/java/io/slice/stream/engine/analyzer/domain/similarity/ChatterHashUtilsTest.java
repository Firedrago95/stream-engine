package io.slice.stream.engine.analyzer.domain.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatterHashUtilsTest {

    @Test
    void 유효한_32자리_16진수_해시를_64비트_정수로_변환한다() {
        String userIdHash = "fa8c6805239ef5373686740f5afb2036";

        Long result = ChatterHashUtils.to64BitHash(userIdHash);

        assertThat(result).isNotNull();
        // 상위 16자리 fa8c6805239ef537의 16진수 언사인드 값 검증
        assertThat(result).isEqualTo(Long.parseUnsignedLong("fa8c6805239ef537", 16));
    }

    @Test
    void 서로_다른_해시는_서로_다른_정수로_변환된다() {
        String hash1 = "fa8c6805239ef5373686740f5afb2036";
        String hash2 = "19e3b97ca1bca954d1ac84cf6862e0dc";

        Long result1 = ChatterHashUtils.to64BitHash(hash1);
        Long result2 = ChatterHashUtils.to64BitHash(hash2);

        assertAll(
            () -> assertThat(result1).isNotNull(),
            () -> assertThat(result2).isNotNull(),
            () -> assertThat(result1).isNotEqualTo(result2)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "anonymous", "ANONYMOUS", "null"})
    void 유효하지_않거나_익명인_해시는_null을_반환한다(String invalidHash) {
        Long result = ChatterHashUtils.to64BitHash(invalidHash);

        assertThat(result).isNull();
    }
}
