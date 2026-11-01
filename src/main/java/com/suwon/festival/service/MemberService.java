package com.suwon.festival.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.LoginMethod;
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
    // 돌려주는 값: 연결 해제할 카카오 회원번호. 외부 호출은 커밋 뒤에 해야 하므로 호출한 쪽에서 함
    // 이미 없는 회원(다른 브라우저에서 탈퇴)이면 지울 것이 없으니 빈 목록
    @Transactional
    public List<String> withdraw(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            return List.of();
        }
        List<String> kakaoUserIds = loginMethodRepository.findByMemberId(memberId).stream()
                .filter(login -> "kakao".equals(login.getProvider()))
                .map(LoginMethod::getProviderId)
                .toList();
        favoriteRepository.deleteByMemberId(memberId);
        loginMethodRepository.deleteByMemberId(memberId);
        memberRepository.deleteById(memberId);
        return kakaoUserIds;
    }
}
