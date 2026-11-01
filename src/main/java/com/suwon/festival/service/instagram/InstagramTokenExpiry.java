package com.suwon.festival.service.instagram;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 인스타 장기 토큰(약 60일) 만료일 알림
//  - Meta 는 서버에서 장기 토큰을 갱신하는 API 가 없음 → 만료 전에 사람이 다시 교환해야 함
//  - 만료일은 scripts/set-instagram-dev 가 교환할 때 instagram.token-expires-at(YYYY-MM-DD)에 저장 (비밀값 아님)
//  - 값이 없으면(단기 토큰 · 예전 설정) 알림 없이 기존처럼 동작
@Component
public class InstagramTokenExpiry {

  static final int WARN_DAYS = 7;

  private final String expiresAt;
  private final Clock clock;

  @Autowired
  public InstagramTokenExpiry(@Value("${instagram.token-expires-at:}") String expiresAt) {
    this(expiresAt, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  InstagramTokenExpiry(String expiresAt, Clock clock) {
    this.expiresAt = expiresAt == null ? "" : expiresAt.strip();
    this.clock = clock;
  }

  // 알릴 것이 없으면 null. 메시지에는 날짜 · 남은 일수만 (토큰 값 X)
  public String warning() {
    if (expiresAt.isEmpty()) return null;
    LocalDate date;
    try {
      date = LocalDate.parse(expiresAt);
    } catch (DateTimeParseException e) {
      return "instagram.token-expires-at 형식이 YYYY-MM-DD 가 아님 → scripts\\set-instagram-dev 로 다시 저장";
    }
    long days = ChronoUnit.DAYS.between(LocalDate.now(clock), date);
    if (days < 0) return "인스타 토큰 만료됨(" + date + ") → scripts\\set-instagram-dev 로 새 토큰 교환";
    if (days <= WARN_DAYS) return "인스타 토큰 " + days + "일 뒤 만료(" + date + ") → scripts\\set-instagram-dev 로 미리 교환";
    return null;
  }
}
