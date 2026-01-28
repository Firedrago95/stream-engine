package io.slice.stream.engine.ingestion.domain.error;

import io.slice.stream.engine.global.error.BusinessException;
import io.slice.stream.engine.global.error.ErrorCode;

public class IngestionException extends BusinessException {

    public IngestionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

