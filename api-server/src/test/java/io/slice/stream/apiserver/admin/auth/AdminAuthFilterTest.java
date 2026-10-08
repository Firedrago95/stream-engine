package io.slice.stream.apiserver.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AdminAuthFilterTest {

    private AdminAuthService adminAuthService;
    private AdminAuthFilter adminAuthFilter;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService("secret1234");
        adminAuthFilter = new AdminAuthFilter(adminAuthService);
    }

    @Test
    void 로그인_경로는_토큰_없이도_필터를_통과한다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 유효한_토큰_헤더가_있으면_관리자_API를_통과한다() throws ServletException, IOException {
        String token = adminAuthService.login("secret1234");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/metrics/overview");
        request.addHeader(AdminAuthFilter.ADMIN_TOKEN_HEADER, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 토큰이_없거나_유효하지_않으면_401_에러를_반환한다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/metrics/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void OPTIONS_프리플라이트_요청은_토큰_없이도_필터를_통과한다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/admin/configs/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 컨텍스트_패스가_존재해도_미인증_관리자_API_요청은_401로_차단된다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/v1/admin/metrics/overview");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 컨텍스트_패스가_존재해도_로그인_경로는_토큰_없이_통과한다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/api/v1/admin/auth/login");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = new MockFilterChain();

        adminAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
