package io.slice.stream.apiserver.admin.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String ADMIN_COOKIE_NAME = "admin_token";
    private static final String ADMIN_PATH_PATTERN = "/api/v1/admin/**";
    private static final String LOGIN_PATH = "/api/v1/admin/auth/login";

    private final AdminAuthService adminAuthService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (pathMatcher.match(ADMIN_PATH_PATTERN, requestPath) && !LOGIN_PATH.equals(requestPath)) {
            String token = resolveToken(request);

            if (!adminAuthService.validateToken(token)) {
                log.warn("[Admin] 미인증 관리자 API 접근 차단: path={}", requestPath);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "관리자 인증이 필요합니다.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String headerToken = request.getHeader(ADMIN_TOKEN_HEADER);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ADMIN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
