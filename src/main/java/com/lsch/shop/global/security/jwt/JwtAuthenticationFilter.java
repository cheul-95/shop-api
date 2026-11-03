package com.lsch.shop.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = jwtProvider.resolveToken(servletRequest);
        if (token != null) {
            try {
                Claims claims = jwtProvider.parse(token);
                Long memberId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                Authentication authentication = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_"+role)));
                SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
                emptyContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(emptyContext);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("JWT 검증 실패: {}", e.getMessage());
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
