package com.lsch.shop.domain.auth.service;

import com.lsch.shop.domain.auth.dto.AuthResponse;
import com.lsch.shop.domain.auth.dto.LoginRequest;
import com.lsch.shop.domain.auth.dto.SignupRequest;
import com.lsch.shop.domain.auth.dto.TokenResponse;
import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.repository.MemberRepository;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import com.lsch.shop.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = new Member(request.email(), encodedPassword, request.name());
        Member saved = memberRepository.save(member);
        return AuthResponse.from(saved);
    }

    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.FAIL_LOGIN));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.FAIL_LOGIN);
        }

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        return TokenResponse.of(accessToken, jwtProvider.getAccessTokenExpirationSeconds());
    }

}