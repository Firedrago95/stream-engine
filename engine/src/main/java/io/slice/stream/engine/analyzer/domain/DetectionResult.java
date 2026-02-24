package io.slice.stream.engine.analyzer.domain;

public record DetectionResult(
    ChatFirepowerStatus status,
    Long firepower
) {
    public static DetectionResult waiting() {
        return new DetectionResult(ChatFirepowerStatus.WAITING, 0L);
    }
}