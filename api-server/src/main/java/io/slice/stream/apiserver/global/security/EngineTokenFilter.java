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

    private final String internalPrefix;
    private final String header;
    private final String secret;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public EngineTokenFilter(
        @Value("${analysis.internal-prefix}") String internalPrefix,
        @Value("${analysis.header}") String header,
        @Value("${analysis.secret}") String secret
    ) {
        this.internalPrefix = internalPrefix;
        this.header = header;
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        if (pathMatcher.match(internalPrefix + "/**", requestPath)) {

            String requestToken = request.getHeader(header);

            if (requestToken == null || !requestToken.equals(secret)) {
                log.warn("유효하지 않은 토큰 접근 차단 : {}", requestPath);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "권한없는 엔진 접근");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
