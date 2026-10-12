package com.suwon.festival.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.google.GoogleUserResponse;
import com.suwon.festival.dto.kakao.KakaoUserResponse;
import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final LoginMethodRepository loginMethodRepository;
  private final MemberRepository memberRepository;
  private final KakaoAuthService kakaoAuthService;
  private final GoogleAuthService googleAuthService;
  private final NaverAuthService naverAuthService;

  // 카카오
  // currentMemberId: 이미 로그인된 상태(세션 있음)라면 그 회원 번호, 아니면 null
  @Transactional
  public Member kakaoLogin(String code, Long currentMemberId) {
    String accessToken = kakaoAuthService.getAccessToken(code);
    KakaoUserResponse kakaoUser = kakaoAuthService.getUserInfo(accessToken);

    return resolveMember("kakao", String.valueOf(kakaoUser.id), kakaoUser.kakao_account.email, currentMemberId);
  }

  // 구글
  @Transactional
  public Member googleLogin(String code, Long currentMemberId) {
    String accessToken = googleAuthService.getAccessToken(code);
    GoogleUserResponse googleUser = googleAuthService.getUserInfo(accessToken);

    return resolveMember("google", googleUser.id, googleUser.email, currentMemberId);
  }

  // 네이버
  @Transactional
  public Member naverLogin(String code, String state, Long currentMemberId) {
    String accessToken = naverAuthService.getAccessToken(code, state);
    var naverUser = naverAuthService.getUserInfo(accessToken);

    return resolveMember("naver", naverUser.id, naverUser.email, currentMemberId);
  }

  // ===== 소셜 로그인 공통 처리: 신규 가입 / 기존 로그인 / 계정 연동 =====
  private Member resolveMember(String provider, String providerId, String email, Long currentMemberId) {
    var existingLoginMethod = loginMethodRepository.findByProviderAndProviderId(provider, providerId);

    if (existingLoginMethod.isPresent()) {
      Member owner = existingLoginMethod.get().getMember();

      // 이미 이 소셜 계정이 "다른" 회원한테 연동되어 있는데,
      // 지금 로그인한 상태에서 또 연동하려는 거면 막아야 함 (계정이 꼬여버림)
      if (currentMemberId != null && !owner.getId().equals(currentMemberId)) {
        throw new IllegalArgumentException("이미 다른 계정에 연동된 소셜 로그인입니다.");
      }

      return owner; // 그냥 평소처럼 로그인
    }

    // 처음 보는 소셜 계정인데, 로그인된 상태(마이페이지에서 "연동하기" 누른 경우)라면
    // 새 회원을 만들지 않고 지금 로그인된 회원한테 로그인 수단만 추가로 붙임
    if (currentMemberId != null) {
      Member currentMember = memberRepository.findById(currentMemberId)
          .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
      attachLoginMethod(provider, providerId, email, currentMember);
      return currentMember;
    }

    return createNewMember(provider, providerId, email);
  }

  private Member createNewMember(String provider, String providerId, String email) {
    Member member = Member.builder()
        .nickname(provider + "_" + providerId)
        .build();
    memberRepository.save(member);

    attachLoginMethod(provider, providerId, email, member);

    return member;
  }

  private void attachLoginMethod(String provider, String providerId, String email, Member member) {
    LoginMethod loginMethod = LoginMethod.builder()
        .provider(provider)
        .providerId(providerId)
        .email(email)
        .password(null)
        .member(member)
        .build();
    loginMethodRepository.save(loginMethod);
  }
}
