package io.slice.stream.engine.sampler.domain;

public record UploadResult(
        boolean success,
        String fileId,
        String webViewLink,
        String errorMessage
) {
    public static UploadResult success(String fileId, String webViewLink) {
        return new UploadResult(true, fileId, webViewLink, null);
    }

    public static UploadResult failure(String errorMessage) {
        return new UploadResult(false, null, null, errorMessage);
    }
}
