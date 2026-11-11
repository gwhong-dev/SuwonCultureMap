package com.suwon.festival.service.instagram;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 지난 인스타 행사 판단 (Meta 약관 3.d.i.2.a "필요 없어지면 삭제", 11/11 사용자 결정: 종료 7일 뒤 삭제)
//  - 기준일 = 종료일, 없으면 시작일. 둘 다 없으면 지우지 않음
//  - 오늘(KST) >= 기준일 + 7일 이면 지난 행사 (종료 6일 뒤 = 남김, 7일 뒤 = 지움)
//  - test 프로필은 꺼 둠(instagram.expire.enabled=false): 시험 캡션 날짜가 실제 날짜가 지나며 사라지지 않게. 이 규칙 시험은 고정 시계로 켬
@Component
public class InstagramExpiry {

  public static final int KEEP_DAYS = 7;

  private final boolean enabled;
  private final Clock clock;

  @Autowired
  public InstagramExpiry(@Value("${instagram.expire.enabled:true}") boolean enabled) {
    this(enabled, Clock.system(InstagramPostProcessor.KST));
  }

  InstagramExpiry(boolean enabled, Clock clock) {
    this.enabled = enabled;
    this.clock = clock;
  }

  public static InstagramExpiry disabled() {
    return new InstagramExpiry(false, Clock.system(InstagramPostProcessor.KST));
  }

  public boolean isEnabled() {
    return enabled;
  }

  // 기준일이 이 날짜 이하이면 지난 행사
  public LocalDate cutoff() {
    return LocalDate.now(clock).minusDays(KEEP_DAYS);
  }

  public boolean isExpired(LocalDate startDate, LocalDate endDate) {
    if (!enabled) return false;
    LocalDate base = endDate != null ? endDate : startDate;
    return base != null && !base.isAfter(cutoff());
  }
}
