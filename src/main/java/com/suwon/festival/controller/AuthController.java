package com.suwon.festival.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.service.AuthService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final LoginMethodRepository loginMethodRepository;

  // ===== 소셜 로그인 설정값 (프로필별 properties에서 주입) =====
  @Value("${kakao.rest-api-key}")
  private String kakaoClientId;
  @Value("${kakao.redirect-uri}")
  private String kakaoRedirectUri;

  @Value("${google.client-id}")
  private String googleClientId;
  @Value("${google.redirect-uri}")
  private String googleRedirectUri;

  @Value("${naver.client-id}")
  private String naverClientId;
  @Value("${naver.redirect-uri}")
  private String naverRedirectUri;

  // ===== 로그인 시작: 프론트는 /api/auth/{제공자}/login 만 호출 =====
  //   → 서버가 현재 환경(dev/prod)에 맞는 인증 주소를 만들어 이동시킴
  //   → HTML에 localhost·client_id를 하드코딩하지 않아도 됨
  @GetMapping("/kakao/login")
  public ResponseEntity<Void> kakaoLogin() {
    String url = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
        .queryParam("client_id", kakaoClientId)
        .queryParam("redirect_uri", kakaoRedirectUri)
        .queryParam("response_type", "code")
        .encode().build().toUriString();
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  @GetMapping("/google/login")
  public ResponseEntity<Void> googleLogin() {
    String url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
        .queryParam("client_id", googleClientId)
        .queryParam("redirect_uri", googleRedirectUri)
        .queryParam("response_type", "code")
        .queryParam("scope", "email profile")
        .encode().build().toUriString();
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  @GetMapping("/naver/login")
  public ResponseEntity<Void> naverLogin() {
    String url = UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/authorize")
        .queryParam("response_type", "code")
        .queryParam("client_id", naverClientId)
        .queryParam("redirect_uri", naverRedirectUri)
        .queryParam("state", "test123")
        .encode().build().toUriString();
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  @GetMapping("/kakao/callback")
  public ResponseEntity<Void> kakaoCallback(@RequestParam String code, HttpSession session) {
    Long currentMemberId = (Long) session.getAttribute("memberId"); // 로그인 상태면 연동, 아니면 신규/일반 로그인
    Member member = authService.kakaoLogin(code, currentMemberId);
    session.setAttribute("memberId", member.getId());
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
  }

  // 구글 콜백
  @GetMapping("/google/callback")
  public ResponseEntity<Void> googleCallback(@RequestParam String code, HttpSession session) {
    Long currentMemberId = (Long) session.getAttribute("memberId");
    Member member = authService.googleLogin(code, currentMemberId);
    session.setAttribute("memberId", member.getId());
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
  }

  // 네이버 콜백
  @GetMapping("/naver/callback")
  public ResponseEntity<Void> naverCallback(@RequestParam String code, @RequestParam String state,
      HttpSession session) {
    Long currentMemberId = (Long) session.getAttribute("memberId");
    Member member = authService.naverLogin(code, state, currentMemberId);
    session.setAttribute("memberId", member.getId());
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
  }

  @GetMapping(value = "/me", produces = "application/json")
  public Map<String, Object> me(HttpSession session) {
    Long memberId = (Long) session.getAttribute("memberId");
    Map<String, Object> result = new HashMap<>();
    result.put("loggedIn", memberId != null);
    result.put("memberId", memberId);

    // ===== 마이페이지에서 "이미 연동된 소셜"을 구분해서 보여주기 위한 목록 =====
    if (memberId != null) {
      List<String> linkedProviders = loginMethodRepository.findByMemberId(memberId).stream()
          .map(loginMethod -> loginMethod.getProvider())
          .toList();
      result.put("linkedProviders", linkedProviders);
    }

    return result;
  }

  @PostMapping("/logout")
  public String logout(HttpSession session) {
    session.invalidate();
    return "로그아웃 완료";
  }

}
