package io.slice.stream.apiserver.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class EngineTokenFilter extends OncePerRequestFilter {

    private final String signalPath;
    private final String syncPath;
    private final String expectedSecret;
    private final String headerName;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public EngineTokenFilter(
        @Value("${analysis.signal.path}") String signalPath,
        @Value("${analysis.sync.path}") String syncPath,
        @Value("${analysis.signal.secret}") String expectedSecret,
        @Value("${analysis.signal.header}") String headerName
    ) {
        this.signalPath = signalPath;
        this.syncPath = syncPath;
        this.expectedSecret = expectedSecret;
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        if (pathMatcher.match(signalPath + "/**", requestPath) ||
            pathMatcher.match(syncPath + "/**", requestPath)) {

            String requestToken = request.getHeader(headerName);

            if (requestToken == null || !requestToken.equals(expectedSecret)) {
                log.warn("유효하지 않은 토큰 접근 차단 : {}", requestPath);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "권한없는 엔진 접근");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
