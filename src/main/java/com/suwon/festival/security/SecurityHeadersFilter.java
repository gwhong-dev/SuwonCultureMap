package com.suwon.festival.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 응답(화면 · API · 오류)에 브라우저 보안 헤더를 붙임.
 *
 * - X-Content-Type-Options: 파일 종류를 브라우저가 멋대로 추측하지 않게 함 (스크립트 위장 차단)
 * - X-Frame-Options + CSP frame-ancestors: 다른 사이트가 이 화면을 iframe 에 넣지 못하게 함 (클릭재킹 차단)
 * - CSP object-src · base-uri · form-action: 플러그인 실행 · base 태그 바꿔치기 · 외부로 폼 전송 차단 (카카오톡 공유 sharer.kakao.com 만 예외)
 * - CSP script-src: 우리 서버 파일(static/js 등)과 카카오 SDK 도메인만 실행. 인라인 스크립트 · onclick 같은 속성 · eval 은 차단
 *   (화면 스크립트는 모두 static/js 로 분리. StaticScriptPolicyTest 가 인라인을 다시 넣지 못하게 막음)
 * - Referrer-Policy: 외부 링크로 나갈 때 주소 전체 대신 도메인만 전달
 * - Permissions-Policy: 쓰지 않는 카메라 · 마이크 · 위치 권한을 막음 (위치 기능을 만들면 geolocation=(self) 로)
 * - Strict-Transport-Security(HSTS): 운영에서만. 1년 동안 이 도메인은 HTTPS 로만 접속
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  // script-src: dapi.kakao.com = 지도 SDK 로더, t1.kakaocdn.net = 카카오톡 공유 SDK + 로더가 이어서 부르는 지도 본체(mapjsapi)
  //   도메인만 적음(scheme 생략) → https 화면에서는 https 만 허용, 로컬 http 에서는 로더가 http 로 부르는 본체도 허용
  // form-action: 카카오톡 공유(JS SDK)가 폰 밖(아이패드 데스크톱 모드 · 좁은 PC 창)에서는 sharer.kakao.com 으로 폼 전송
  static final String CSP =
      "script-src 'self' dapi.kakao.com t1.kakaocdn.net; "
          + "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self' https://sharer.kakao.com";
  static final String HSTS = "max-age=31536000";

  private final boolean hstsEnabled;

  public SecurityHeadersFilter(@Value("${app.security.hsts:false}") boolean hstsEnabled) {
    this.hstsEnabled = hstsEnabled;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // 응답 본문을 쓰기 전에 붙여야 하므로 다음 단계로 넘기기 전에 설정
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    if (hstsEnabled) {
      response.setHeader("Strict-Transport-Security", HSTS);
    }
    chain.doFilter(request, response);
  }

  // 오류 화면(/error)으로 다시 넘어갈 때도 헤더가 남도록
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }
}
