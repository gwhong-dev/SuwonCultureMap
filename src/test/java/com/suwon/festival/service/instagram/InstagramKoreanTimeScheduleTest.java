package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.CaptionDateParser;
import com.suwon.festival.service.caption.ParsedEvent;
import com.suwon.festival.service.schedule.ScheduleResult;
import com.suwon.festival.service.schedule.ScheduleResult.Status;

// 한글 시간 원문("오후 7시")이 진행 날짜 추출(#125, 요일 칸)에 들어가도 엉뚱한 날짜 · 요일이 생기지 않는지
// 달력 기준: 2026-11-06 금, 2026-11-07 토, 2026-11-15 일
class InstagramKoreanTimeScheduleTest {

  private static ScheduleResult schedule(String dateLine) {
    CaptionDateParser.Result d = CaptionDateParser.parse(dateLine);
    return InstagramPostProcessor.schedule(
        new ParsedEvent(true, "가상 행사", "행사", d.start(), d.end(), d.timeText(), "가상 아트홀", dateLine));
  }

  private static ScheduleResult withoutTime(String dateLine) {
    CaptionDateParser.Result d = CaptionDateParser.parse(dateLine);
    return InstagramPostProcessor.schedule(
        new ParsedEvent(true, "가상 행사", "행사", d.start(), d.end(), null, "가상 아트홀", dateLine));
  }

  @Test
  void 하루짜리는_그날만() {
    ScheduleResult r = schedule("2026. 11. 7.(토) 오후 7시");
    assertThat(r.dates()).containsExactly(LocalDate.of(2026, 11, 7));
  }

  @Test
  void 기간은_시간_원문이_없을_때와_같은_결과() {
    for (String line : new String[] {
        "2026. 11. 6.(금) ~ 11. 15.(일) 오후 7시",
        "2026. 11. 6.(금) ~ 11. 15.(일) 저녁 7시 30분",
        "2026. 11. 6.(금) ~ 11. 15.(일) 19시~21시",
        "2026. 11. 6.(금) ~ 11. 15.(일) 낮 12시" }) {
      ScheduleResult r = schedule(line);
      assertThat(r.status()).as(line).isEqualTo(withoutTime(line).status());
      assertThat(r.dates()).as(line).isEqualTo(withoutTime(line).dates());
      assertThat(r.status()).as(line).isNotEqualTo(Status.DATES); // 요일 · 날짜가 새로 생기지 않음
    }
  }
}
