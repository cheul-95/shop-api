package com.lsch.shop.global.security.jwt;

import com.lsch.shop.domain.member.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;   // ms

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token.expiration}") long accessTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createAccessToken(Long memberId, Role role) {
        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + accessTokenExpiration);
        return Jwts.builder()
                .subject(memberId.toString())
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expirationTime)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMillis(accessTokenExpiration).toSeconds();
    }
}