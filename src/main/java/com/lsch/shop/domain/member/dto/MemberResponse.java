package com.lsch.shop.domain.member.dto;

import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.entity.Role;


public record MemberResponse(Long id, String email, String name, Role role) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole()
        );
    }
}
