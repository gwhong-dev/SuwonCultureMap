package com.suwon.festival.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.repository.LoginMethodRepository;

import lombok.RequiredArgsConstructor;

/**
 * 카카오 "연결 끊기 알림" 처리: 사용자가 카카오 쪽(연결된 서비스 관리 · 계정 삭제 · 고객센터)에서 앱 연결을 끊었을 때.
 * - 그 카카오 로그인 수단을 지움
 * - 회원에게 다른 로그인 수단(구글 · 네이버)이 남으면 회원 · 찜은 그대로
 * - 카카오만 쓰던 회원이면 더는 로그인할 수 없으므로 탈퇴와 똑같이 회원 · 로그인 수단 · 찜을 지움 (11/9 사용자 결정 A)
 * 카카오 연결은 이미 끊겼으므로 연결 해제 API 는 다시 부르지 않음
 */
@Service
@RequiredArgsConstructor
public class KakaoUnlinkService {

  public enum Result {
    NOT_FOUND, // 우리 DB 에 없는 카카오 회원 (이미 탈퇴 등)
    LOGIN_REMOVED, // 카카오 로그인만 지움, 다른 로그인 수단이 남음
    MEMBER_DELETED // 카카오만 쓰던 회원 → 회원 전체 삭제
  }

  private final LoginMethodRepository loginMethodRepository;
  private final MemberService memberService;

  @Transactional
  public Result handleUnlink(String kakaoUserId) {
    var found = loginMethodRepository.findByProviderAndProviderId("kakao", kakaoUserId);
    if (found.isEmpty()) {
      return Result.NOT_FOUND;
    }
    LoginMethod kakaoLogin = found.get();
    Long memberId = kakaoLogin.getMember().getId();
    boolean hasOtherLogin = loginMethodRepository.findByMemberId(memberId).stream()
        .anyMatch(login -> !login.getId().equals(kakaoLogin.getId()));
    if (hasOtherLogin) {
      loginMethodRepository.delete(kakaoLogin);
      return Result.LOGIN_REMOVED;
    }
    memberService.withdraw(memberId); // 돌려주는 카카오 회원번호는 쓰지 않음 (이미 끊김)
    return Result.MEMBER_DELETED;
  }
}
