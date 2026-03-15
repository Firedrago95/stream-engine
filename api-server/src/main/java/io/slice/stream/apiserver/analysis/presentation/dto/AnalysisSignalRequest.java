package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record AnalysisSignalRequest(
    @NotBlank(message = "스트림 ID는 필수입니다.")
    String streamId,

    @NotBlank(message = "상태 값은 필수입니다.")
    String status,

    @NotNull(message = "타임스탬프는 필수입니다.")
    Instant timestamp,

    @PositiveOrZero(message = "화력 수치는 0 이상이어야 합니다.")
    long firepower,

    Long offsetMs
) {
    public AnalysisSignal toDomain() {
        return AnalysisSignal.of(streamId, status, timestamp, firepower, offsetMs);
    }
}
