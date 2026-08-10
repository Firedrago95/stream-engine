package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record AnalysisSignalRequest(
    @NotBlank(message = "스트림 ID는 필수입니다.")
    String streamId,

    @NotBlank(message = "라이브 ID는 필수입니다.")
    String liveId,

    @NotBlank(message = "상태 값은 필수입니다.")
    String status,

    @NotNull(message = "타임스탬프는 필수입니다.")
    Instant timestamp,

    @PositiveOrZero(message = "화력 수치는 0 이상이어야 합니다.")
    long firepower,

    @NotNull(message = "VOD 오프셋 시간은 필수입니다.")
    Long offsetMs
) {
    public AnalysisSignal toDomain() {
        // 엔진은 세션 개념을 모르므로, sessionId는 null로 임시 세팅
        return AnalysisSignal.of(streamId, liveId, status, timestamp, firepower, offsetMs);
    }
}
