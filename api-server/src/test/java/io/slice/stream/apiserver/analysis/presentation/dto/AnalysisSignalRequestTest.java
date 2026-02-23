package io.slice.stream.apiserver.analysis.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisSignalRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 화력_수치가_음수이면_검증에_실패한다() {
        // given
        AnalysisSignalRequest request = new AnalysisSignalRequest(
            "test-stream", "PEAK", Instant.now(), -100L // 음수 화력
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("0 이상"));
    }

    @Test
    void 필수_값이_누락되면_검증에_실패한다() {
        // given
        AnalysisSignalRequest request = new AnalysisSignalRequest(
            "", null, null, 500L // 빈 ID, null 상태, null 시간
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(3);
    }

    @Test
    void 모든_값이_정상이면_도메인으로_올바르게_변환된다() {
        // given
        String streamId = "valid-id";
        long firepower = 1234L;
        AnalysisSignalRequest request = new AnalysisSignalRequest(
            streamId, "NORMAL", Instant.now(), firepower
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
        assertThat(request.toDomain().firepower()).isEqualTo(firepower);
    }
}
