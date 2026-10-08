package io.slice.stream.apiserver.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AdminAuthServiceTest {

    @Test
    void 시크릿_키가_null이거나_공백이면_초기화_시_예외가_발생한다() {
        assertThatThrownBy(() -> new AdminAuthService(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("관리자 시크릿 키");

        assertThatThrownBy(() -> new AdminAuthService("   "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("관리자 시크릿 키");
    }

    @Test
    void 올바른_비밀번호로_로그인하면_세션_토큰을_발급한다() {
        AdminAuthService service = new AdminAuthService("secret1234");

        String token = service.login("secret1234");

        assertThat(token).isNotBlank();
        assertThat(service.validateToken(token)).isTrue();
    }

    @Test
    void 잘못된_비밀번호로_로그인하면_예외가_발생한다() {
        AdminAuthService service = new AdminAuthService("secret1234");

        assertThatThrownBy(() -> service.login("wrong-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비밀번호가 일치하지 않습니다");
    }

    @Test
    void 로그아웃하면_토큰이_만료_처리된다() {
        AdminAuthService service = new AdminAuthService("secret1234");
        String token = service.login("secret1234");

        service.logout(token);

        assertThat(service.validateToken(token)).isFalse();
    }
}
