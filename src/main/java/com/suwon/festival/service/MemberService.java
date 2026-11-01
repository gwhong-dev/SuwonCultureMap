package com.suwon.festival.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final LoginMethodRepository loginMethodRepository;
    private final FavoriteRepository favoriteRepository;

    public Member signup(String nickname) {

        // 닉네임 중ㅇ복 확인
        if (memberRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 존재하는 닉네임 입니다.");
        }

        // 새 member 만들어서 저장
        Member member = Member.builder()
                .nickname(nickname)
                .build();

        return memberRepository.save(member);
    }

    // 회원 탈퇴: 이 회원의 개인정보(찜 · 로그인 수단 · 회원)를 모두 지움. FK 때문에 자식부터
    // 소셜 쪽 연결(앱 동의)은 그대로 남음: 토큰을 저장하지 않아 서버에서 끊을 수 없음
    @Transactional
    public void withdraw(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다.");
        }
        favoriteRepository.deleteByMemberId(memberId);
        loginMethodRepository.deleteByMemberId(memberId);
        memberRepository.deleteById(memberId);
    }
}
