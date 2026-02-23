package io.slice.stream.apiserver.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EngineTokenFilterTest {

    private EngineTokenFilter filter;
    private final String signalPath = "/gate/hidden-room";
    private final String secret = "shh-very-secret";
    private final String headerName = "MAGIC-KEY";

    @BeforeEach
    void setUp() {
        filter = new EngineTokenFilter(signalPath, secret, headerName);
    }

    @Test
    void 올바른_경로와_올바른_토큰이_오면_필터를_통과시킨다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(signalPath);
        request.addHeader(headerName, secret);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response); // 다음 필터로 진행됨
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void 은닉_경로인데_토큰이_틀리면_401_에러를_반환한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(signalPath);
        request.addHeader(headerName, "wrong-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain, never()).doFilter(request, response); // 필터 중단됨
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void 보안_경로가_아닌_일반_API_요청은_검증_없이_통과시킨다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/public/streams"); // 검증 대상 아님

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
