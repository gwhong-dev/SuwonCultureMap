package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.ParsedEvent;
import com.suwon.festival.service.schedule.ScheduleResult;
import com.suwon.festival.service.schedule.ScheduleResult.Status;

// 인스타 행사의 진행 날짜 규칙 (설계 docs/event-schedule.md 9번 · 12번 결정 2 · 3 · 6)
class InstagramScheduleTest {

  private static final LocalDate NOV_6 = LocalDate.of(2026, 11, 6);
  private static final LocalDate NOV_15 = LocalDate.of(2026, 11, 15);

  private static ScheduleResult schedule(LocalDate start, LocalDate end, String dateLine, String timeText) {
    return InstagramPostProcessor.schedule(new ParsedEvent(true, "가상 행사", "행사", start, end, timeText, "가상 아트홀", dateLine));
  }

  @Test
  void 매주_없이_요일만_있으면_펼치지_않음() {
    ScheduleResult r = schedule(NOV_6, NOV_15, "2026. 11. 6.(금) ~ 11. 15.(일)", "토요일 14:00");
    assertThat(r.status()).isEqualTo(Status.UNKNOWN); // 결정 2: idx 2972 처럼 실제 2회일 수 있음
  }

  @Test
  void 매주_요일은_시간_줄에_있어도_읽음() {
    ScheduleResult r = schedule(NOV_6, NOV_15, "2026. 11. 6.(금) ~ 11. 15.(일)", "매주 금 19:00");
    assertThat(r.dates()).containsExactly(NOV_6, LocalDate.of(2026, 11, 13));
    assertThat(r.sourceText()).isEqualTo("매주 금 19:00");
  }

  @Test
  void 공휴일_조건이_있으면_표시() {
    ScheduleResult r = schedule(NOV_6, NOV_15, "2026. 11. 6.(금) ~ 11. 15.(일)", "매주 토~일, 공휴일");
    assertThat(r.status()).isEqualTo(Status.DATES);
    assertThat(r.holidayNote()).isTrue();
  }

  @Test
  void 매주에_조건이_붙으면_모름() {
    assertThat(schedule(NOV_6, NOV_15, "2026. 11. 6.(금) ~ 11. 15.(일)", "매주 토 (우천 시 취소)").status())
        .isEqualTo(Status.UNKNOWN);
  }

  @Test
  void 날짜_줄이_없어도_하루짜리는_그날() {
    ScheduleResult r = schedule(NOV_6, NOV_6, null, null); // AI 결과처럼 원문 줄이 없는 경우
    assertThat(r.dates()).containsExactly(NOV_6);
  }
}
