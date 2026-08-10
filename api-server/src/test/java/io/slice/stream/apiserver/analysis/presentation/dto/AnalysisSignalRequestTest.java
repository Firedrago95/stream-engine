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
            "test-stream", "test-live-id", "PEAK", Instant.now(), -100L, 1000L // 음수 화력
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firepower"));
    }

    @Test
    void 필수_값이_누락되면_검증에_실패한다() {
        // given
        AnalysisSignalRequest request = new AnalysisSignalRequest(
            "", null, null, null, 500L, 1000L // 빈 ID, null 상태, null 시간
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(4);
    }

    @Test
    void 모든_값이_정상이면_도메인으로_올바르게_변환된다() {
        // given
        String streamId = "valid-id";
        long firepower = 1234L;
        long offsetMs = 5000L;
        AnalysisSignalRequest request = new AnalysisSignalRequest(
            streamId, "test-live-id", "NORMAL", Instant.now(), firepower, offsetMs
        );

        // when
        Set<ConstraintViolation<AnalysisSignalRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
        assertThat(request.toDomain().firepower()).isEqualTo(firepower);
    }
}
