package io.slice.stream.apiserver.admin.presentation.dto;

public record AdminLoginResponse(
    String token,
    long expiresInSeconds
) {
}
