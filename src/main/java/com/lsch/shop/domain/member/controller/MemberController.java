package com.lsch.shop.domain.member.controller;

import com.lsch.shop.domain.member.dto.MemberResponse;
import com.lsch.shop.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public MemberResponse getMe(@AuthenticationPrincipal Long memberId) {

        return memberService.getMe(memberId);

    }
}
