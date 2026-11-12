package com.suwon.festival.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final NicknameGenerator nicknameGenerator;

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

    // 닉네임이 없으면(가입 직후 · V9 전 가입한 회원) 랜덤으로 붙여 저장. 있으면 그대로
    @Transactional
    public String ensureNickname(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (member.getNickname() == null) {
            member.changeNickname(nicknameGenerator.generate(memberRepository::existsByNickname));
            memberRepository.saveAndFlush(member); // UNIQUE 충돌을 여기서 바로 알게
        }
        return member.getNickname();
    }

    // 마이페이지 "다시 뽑기" 용 제안 (저장은 안 함)
    @Transactional(readOnly = true)
    public String suggestNickname() {
        return nicknameGenerator.generate(memberRepository::existsByNickname);
    }

    // 닉네임 바꾸기: 규칙 · 중복 확인 뒤 저장. 정리된 이름을 돌려줌
    @Transactional
    public String changeNickname(Long memberId, String input) {
        String nickname = NicknameGenerator.normalize(input);
        String problem = NicknameGenerator.validate(nickname);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw new IllegalArgumentException("이미 다른 회원이 쓰는 닉네임이에요.");
        }
        member.changeNickname(nickname);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException sameMoment) {
            // 확인과 저장 사이에 다른 회원이 같은 이름을 먼저 저장한 경우 (UNIQUE 가 막음)
            throw new IllegalArgumentException("이미 다른 회원이 쓰는 닉네임이에요.");
        }
        return nickname;
    }
}
