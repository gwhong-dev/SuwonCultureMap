package com.suwon.festival.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.service.KakaoUnlinkService;

import lombok.RequiredArgsConstructor;

/**
 * 카카오 연결 끊기 알림(콜백): 카카오 콘솔 [카카오 로그인] > [연결 끊기 알림] 에 이 주소를 등록.
 * 카카오가 GET(쿼리) 또는 POST(폼)로 app_id · user_id · referrer_type 을 보내고,
 * 헤더 Authorization: KakaoAK {어드민 키} 로 자기가 보냈음을 증명함 → 우리 어드민 키와 같아야만 처리.
 * 카카오는 응답과 상관없이 연결을 끊음. 오류 응답이 잦으면 콜백이 꺼질 수 있으므로 정상 요청은 항상 200.
 */
@RestController
@RequiredArgsConstructor
public class KakaoUnlinkCallbackController {

  private static final Logger log = LoggerFactory.getLogger(KakaoUnlinkCallbackController.class);

  private final KakaoUnlinkService kakaoUnlinkService;

  @Value("${kakao.admin-key:}")
  private String adminKey;

  @RequestMapping(value = "/api/auth/kakao/unlink-callback", method = { RequestMethod.GET, RequestMethod.POST })
  public ResponseEntity<Void> unlinkCallback(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestParam(value = "user_id", required = false) String userId,
      @RequestParam(value = "referrer_type", required = false) String referrerType) {
    if (adminKey == null || adminKey.isBlank()) {
      log.warn("카카오 어드민 키가 없어 연결 끊기 알림을 확인하지 못함 (kakao.admin-key)");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!sameKey(authorization, "KakaoAK " + adminKey)) {
      log.warn("카카오 연결 끊기 알림: 인증 헤더가 맞지 않아 거절");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (userId == null || userId.isBlank()) {
      log.warn("카카오 연결 끊기 알림: user_id 없음");
      return ResponseEntity.ok().build();
    }
    KakaoUnlinkService.Result result;
    try {
      result = kakaoUnlinkService.handleUnlink(userId.trim());
    } catch (RuntimeException e) {
      // 같은 알림이 동시에 두 번 와서 늦은 쪽 삭제가 실패하는 경우 등. 오류 응답이 쌓이면 콜백이 꺼질 수 있어 200
      // 메시지에 회원번호가 들어갈 수 있으므로 예외 종류만 남김
      log.error("카카오 연결 끊기 알림 처리 실패 referrer_type={} {}", safe(referrerType), e.getClass().getSimpleName());
      return ResponseEntity.ok().build();
    }
    // 회원번호는 남기지 않음 → 경로 · 결과만
    log.info("카카오 연결 끊기 알림 처리 referrer_type={} result={}", safe(referrerType), result);
    return ResponseEntity.ok().build();
  }

  // 내용 비교 시간을 같게 (키를 한 글자씩 맞혀 보는 공격 방지, 길이 차이는 드러날 수 있으나 키 형식이 정해져 있음)
  private static boolean sameKey(String given, String expected) {
    if (given == null) return false;
    return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  // 로그 줄 바꿈 · 긴 값 방지 (카카오가 정한 값은 영문 대문자 · 밑줄)
  private static String safe(String value) {
    if (value == null) return "-";
    String cleaned = value.replaceAll("[^A-Za-z_]", "");
    return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
  }
}
