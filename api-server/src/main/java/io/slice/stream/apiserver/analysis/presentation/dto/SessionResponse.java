package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;

public record SessionResponse(String sessionId, Instant startedAt) {

}
