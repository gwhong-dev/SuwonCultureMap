package com.suwon.festival.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;

/**
 * OAuth state 값 발급·검증 (CSRF 방지).
 *
 * 로그인 시작 때 추측할 수 없는 무작위 값을 만들어 세션에 저장하고 인증 주소에 붙인다.
 * 콜백으로 돌아온 state 가 세션 값과 같을 때만 로그인을 진행한다.
 * → 다른 사이트가 "공격자 계정의 code" 로 콜백을 강제로 열게 해도 state 가 없어서 막힌다.
 */
@Component
public class OAuthStateManager {

  private static final String KEY_PREFIX = "oauthState:";
  private static final SecureRandom RANDOM = new SecureRandom();

  /** 새 state 를 만들어 세션에 저장하고 돌려준다 (제공자별로 따로 저장). */
  public String issue(HttpSession session, String provider) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    session.setAttribute(KEY_PREFIX + provider, state);
    return state;
  }

  /** 콜백의 state 가 세션 값과 같은지 확인. 한 번 쓰면 지워서 재사용을 막는다. */
  public boolean verify(HttpSession session, String provider, String state) {
    Object saved = session.getAttribute(KEY_PREFIX + provider);
    session.removeAttribute(KEY_PREFIX + provider);
    if (!(saved instanceof String expected) || state == null) {
      return false;
    }
    // 길이·내용 비교 시간을 일정하게 (타이밍 공격 방지)
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8));
  }
}
