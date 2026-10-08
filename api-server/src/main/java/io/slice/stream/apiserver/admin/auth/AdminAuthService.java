package io.slice.stream.apiserver.admin.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminAuthService {

    private static final long TOKEN_VALIDITY_SECONDS = 86400L; // 24시간

    private final String adminSecretKey;
    private final Map<String, Instant> tokenStorage = new ConcurrentHashMap<>();

    public AdminAuthService(
        @Value("${admin.secret-key:${ADMIN_SECRET_KEY:}}") String adminSecretKey
    ) {
        if (adminSecretKey == null || adminSecretKey.isBlank()) {
            this.adminSecretKey = UUID.randomUUID().toString();
            log.warn("[Admin] 관리자 시크릿 키(ADMIN_SECRET_KEY)가 설정되지 않아 임의 생성 키가 적용되었습니다: {}", this.adminSecretKey);
        } else {
            this.adminSecretKey = adminSecretKey;
        }
    }

    public String login(String password) {
        if (password == null || !adminSecretKey.equals(password.trim())) {
            log.warn("[Admin] 유효하지 않은 관리자 비밀번호 입력 시도");
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS);
        tokenStorage.put(token, expiresAt);
        log.info("[Admin] 관리자 로그인 성공, 세션 토큰 발급 완료 (유효시간: 24시간)");
        return token;
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        Instant expiresAt = tokenStorage.get(token);
        if (expiresAt == null) {
            return false;
        }

        if (Instant.now().isAfter(expiresAt)) {
            tokenStorage.remove(token);
            log.info("[Admin] 만료된 관리자 세션 토큰 제거: {}", token);
            return false;
        }

        return true;
    }

    public void logout(String token) {
        if (token != null) {
            tokenStorage.remove(token);
        }
    }
}
