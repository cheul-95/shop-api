package com.lsch.shop.domain.member.service;

import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.repository.MemberRepository;
import com.lsch.shop.domain.member.dto.MemberResponse;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

}
