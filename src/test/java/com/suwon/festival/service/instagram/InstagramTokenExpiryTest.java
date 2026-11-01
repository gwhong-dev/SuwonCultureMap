package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

// 장기 토큰 만료일 알림 — 기준일 2026-11-01(서울)로 고정
class InstagramTokenExpiryTest {

  private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  private static String warning(String expiresAt) {
    return new InstagramTokenExpiry(expiresAt, TODAY).warning();
  }

  @Test
  void 만료일_없으면_알림_없음() {
    assertThat(warning("")).isNull();
    assertThat(warning(null)).isNull();
    assertThat(warning("   ")).isNull();
  }

  @Test
  void 여유_있으면_알림_없음() {
    assertThat(warning("2026-11-09")).isNull(); // 8일 남음
    assertThat(warning("2026-12-31")).isNull();
  }

  @Test
  void 칠일_이내면_남은_일수_알림() {
    assertThat(warning("2026-11-08")).contains("7일 뒤 만료").contains("2026-11-08");
    assertThat(warning("2026-11-01")).contains("0일 뒤 만료");
  }

  @Test
  void 지났으면_만료_알림() {
    assertThat(warning("2026-10-31")).contains("만료됨").contains("2026-10-31");
  }

  @Test
  void 형식이_틀리면_다시_저장_안내() {
    assertThat(warning("2026/11/30")).contains("형식");
    assertThat(warning("1735689600")).contains("형식"); // 만료 시각 숫자를 잘못 넣은 경우
  }
}
