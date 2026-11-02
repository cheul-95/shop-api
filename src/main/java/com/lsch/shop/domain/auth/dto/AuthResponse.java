package com.lsch.shop.domain.auth.dto;

import com.lsch.shop.domain.member.entity.Member;

public record AuthResponse(String email, String name) {
    public static AuthResponse from(Member member) {
        return new AuthResponse(
                member.getEmail(),
                member.getName()
        );
    }
}
