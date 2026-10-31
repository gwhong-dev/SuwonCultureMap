package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.CaptionDateParser.Kind;
import com.suwon.festival.service.caption.CaptionDateParser.Result;

// 날짜 줄 읽기 — 예시는 실제 캡션이 아니라 같은 "모양"으로 직접 만든 값 (설계 문서 3-1)
// 달력 기준: 2026-11-05 목, 2026-11-07 토, 2026-12-28 월, 2027-01-03 일
class CaptionDateParserTest {

  @Test
  void 시작_종료_시간이_모두_있는_줄() {
    Result r = CaptionDateParser.parse("2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00");
    assertThat(r.kind()).isEqualTo(Kind.OK);
    assertThat(r.start()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(r.end()).isEqualTo(LocalDate.of(2026, 11, 7)); // 종료일 연도 생략 → 같은 줄 시작일 연도
    assertThat(r.timeText()).isEqualTo("19:00~21:00");
  }

  @Test
  void 하루짜리_시간_없음() {
    Result r = CaptionDateParser.parse("2026. 11. 5.(목)");
    assertThat(r.kind()).isEqualTo(Kind.OK);
    assertThat(r.start()).isEqualTo(r.end());
    assertThat(r.timeText()).isNull();
  }

  @Test
  void 띄어쓰기_없는_점_날짜와_전각_물결() {
    Result r = CaptionDateParser.parse("2026.11.05 ～ 2026.11.07");
    assertThat(r.kind()).isEqualTo(Kind.OK);
    assertThat(r.end()).isEqualTo(LocalDate.of(2026, 11, 7));
  }

  @Test
  void 해를_넘기는_기간은_종료_연도_더하기_1() {
    Result r = CaptionDateParser.parse("2026. 12. 28.(월) ~ 1. 3.(일)");
    assertThat(r.kind()).isEqualTo(Kind.OK);
    assertThat(r.end()).isEqualTo(LocalDate.of(2027, 1, 3));
  }

  @Test
  void 요일이_달력과_다르면_INVALID() {
    assertThat(CaptionDateParser.parse("2026. 11. 5.(금)").kind()).isEqualTo(Kind.INVALID);
    // 해 넘김 +1 을 했는데 요일이 안 맞음 → 넘김 추정이 틀렸을 수 있으니 버림
    assertThat(CaptionDateParser.parse("2026. 12. 28.(월) ~ 1. 3.(토)").kind()).isEqualTo(Kind.INVALID);
  }

  @Test
  void 연도를_적은_종료일이_시작보다_앞서면_INVALID() {
    assertThat(CaptionDateParser.parse("2026. 11. 7. ~ 2026. 11. 5.").kind()).isEqualTo(Kind.INVALID);
  }

  @Test
  void 없는_날짜는_INVALID() {
    assertThat(CaptionDateParser.parse("2026. 2. 30.").kind()).isEqualTo(Kind.INVALID);
    assertThat(CaptionDateParser.parse("2026. 13. 1.").kind()).isEqualTo(Kind.INVALID);
  }

  @Test
  void 연도가_없으면_추측하지_않고_NO_DATE() {
    Result r = CaptionDateParser.parse("11. 7.(토) ~ 11. 18.(수)");
    assertThat(r.kind()).isEqualTo(Kind.NO_DATE);
    assertThat(r.start()).isNull();
  }

  @Test
  void 시작일_없이_종료일만() {
    Result r = CaptionDateParser.parse("~ 2026. 11. 30.(월)까지");
    // "~ YYYY. M. D." 은 연도 있는 날짜가 있어도 시작일이 아님 → 시작일로 쓰지 않음
    assertThat(r.kind()).isEqualTo(Kind.NO_DATE);
    assertThat(r.reason()).isEqualTo("시작일 없음");
  }

  @Test
  void 월만_있고_일이_없음() {
    Result r = CaptionDateParser.parse("2026. 11월 중");
    assertThat(r.kind()).isEqualTo(Kind.NO_DATE);
    assertThat(r.reason()).isEqualTo("일(日) 없음");
  }

  @Test
  void 매주_반복은_NO_DATE_시간_원문은_보관() {
    Result r = CaptionDateParser.parse("매주 금~일요일 10:00~18:00");
    assertThat(r.kind()).isEqualTo(Kind.NO_DATE);
    assertThat(r.timeText()).isEqualTo("매주 금~일요일 10:00~18:00");
  }

  @Test
  void 범위가_아닌_날짜가_여러_개면_고르지_않고_AI_몫() {
    Result r = CaptionDateParser.parse("2026. 11. 5.(목), 11. 12.(목) 14:00");
    assertThat(r.kind()).isEqualTo(Kind.UNREADABLE);
    assertThat(r.reason()).contains("여러");
  }

  @Test
  void 물결_뒤_종료일_모양을_못_읽으면_하루짜리로_두지_않음() {
    // "~ 7.(토)" 를 무시하면 11/5 하루짜리로 잘못 올라감
    assertThat(CaptionDateParser.parse("2026. 11. 5.(목) ~ 7.(토)").kind()).isEqualTo(Kind.UNREADABLE);
    assertThat(CaptionDateParser.parse("2026. 11. 5. ~ 7.").kind()).isEqualTo(Kind.UNREADABLE);
  }

  @Test
  void 연도는_있는데_모양이_다르면_AI_몫() {
    assertThat(CaptionDateParser.parse("2026년 11월 5일(목)").kind()).isEqualTo(Kind.UNREADABLE);
    assertThat(CaptionDateParser.parse("2026-11-05").kind()).isEqualTo(Kind.UNREADABLE);
  }

  @Test
  void 시간_앞_슬래시는_떼고_보관() {
    assertThat(CaptionDateParser.parse("2026. 11. 5.(목) / 19:00").timeText()).isEqualTo("19:00");
  }
}
