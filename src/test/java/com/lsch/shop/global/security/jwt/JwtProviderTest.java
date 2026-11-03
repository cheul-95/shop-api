package com.lsch.shop.global.security.jwt;

import com.lsch.shop.domain.member.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "ZtvUc0lv6YdvlSBUp9GCsIh9hH92cYwA3oSpXu0jnIA";
    private static final String OTHER_SECRET = "3HG1DaGk4WJb8WkzUN0jDZEpE4WT4aIn5m15FyaxqsC";
    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3_600_000);

    @Test
    void 정상_토큰은_memberId와_role을_꺼낼_수_있다() {
        String accessToken = jwtProvider.createAccessToken(1L, Role.USER);
        Claims claims = jwtProvider.parse(accessToken);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void 만료된_토큰은_ExpiredJwtException() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, -1_000);
        String accessToken = expiredProvider.createAccessToken(1L, Role.USER);
        assertThatThrownBy(() -> expiredProvider.parse(accessToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 다른_키로_서명된_토큰은_SignatureException() {
        JwtProvider otherJwtProvider = new JwtProvider(OTHER_SECRET, 3_600_000);
        String otherAccessToken = otherJwtProvider.createAccessToken(1L, Role.USER);
        assertThatThrownBy(() -> jwtProvider.parse(otherAccessToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 형식이_잘못된_토큰은_MalformedJwtException() {
        assertThatThrownBy(() -> jwtProvider.parse("abc"))
                .isInstanceOf(MalformedJwtException.class);

    }
}
