package io.slice.stream.engine.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C-001"),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C-002"),

    // Ingestion
    STREAM_PROVIDER_CLIENT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "I-001"),
    STREAM_NOT_FOUND(HttpStatus.NOT_FOUND, "I-002"),
    INVALID_STREAM_PROVIDER(HttpStatus.BAD_REQUEST, "I-003"),

    // Analysis
    ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "A-001"),
    ;

    private final HttpStatus status;
    private final String code;

}
