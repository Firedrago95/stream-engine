package io.slice.stream.apiserver.global.error;

import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorResponse {

    private String code;
    private String message;
    private int status;
    private Map<String, String> errors;
    private Instant timestamp;

    private ErrorResponse(ErrorCode code, Map<String, String> errors) {
        this.message = "잘못된 입력값입니다.";
        this.status = code.getStatus().value();
        this.errors = errors;
        this.code = code.getCode();
        this.timestamp = Instant.now();
    }

    private ErrorResponse(ErrorCode code, String message) {
        this.message = message;
        this.status = code.getStatus().value();
        this.code = code.getCode();
        this.timestamp = Instant.now();
    }

    public static ErrorResponse of(ErrorCode code, Map<String, String> errors) {
        return new ErrorResponse(code, errors);
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code, message);
    }

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code, code.getStatus().getReasonPhrase());
    }
}
