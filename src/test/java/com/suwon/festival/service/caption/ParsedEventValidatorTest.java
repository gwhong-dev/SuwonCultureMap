package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// 정리 결과 다시 검사 (설계 문서 5번 표)
class ParsedEventValidatorTest {

  static final String BLOCK = "가을 음악회\n🗓️ 2026. 11. 5.(목) ~ 11. 7.(토)\n📍 가상 아트홀";

  private static ParsedEvent event(LocalDate start, LocalDate end, String place) {
    return new ParsedEvent(true, "가을 음악회", "공연", start, end, null, place);
  }

  @Test
  void 원문과_맞으면_PARSED() {
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7), "가상 아트홀"), BLOCK);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(r.warnings()).isEmpty();
  }

  @Test
  void 원문에_없는_날짜는_FAILED_환각_방지() {
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 20), null, null), BLOCK);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
    assertThat(r.reason()).isEqualTo("시작일이 원문에 없음");
  }

  @Test
  void 낱개_숫자_일치는_날짜로_인정하지_않음() {
    // "11"과 "5"가 따로 있어도 "11. 5." 꼴이 아니면 불인정
    String text = "2026년 11번째 행사, 5개 부스";
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 5), null, null), text);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void 연도가_원문에_없으면_FAILED() {
    String text = "가을 음악회 11. 5.(목)";
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 5), null, null), text);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
    assertThat(r.reason()).contains("연도");
  }

  @Test
  void 본문의_다른_연도와_월일을_짜_맞추면_FAILED() {
    String text = BLOCK + "\n2025년 수상작 재공연 #2025수원";
    var r = ParsedEventValidator.validate(event(LocalDate.of(2025, 11, 5), null, null), text);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void 소수는_날짜로_인정하지_않음() {
    String text = "2026년 공연, 러닝타임 1.5시간, 참가비 3.5만원";
    assertThat(ParsedEventValidator.monthDayInText(text, LocalDate.of(2026, 1, 5))).isFalse();
    assertThat(ParsedEventValidator.monthDayInText(text, LocalDate.of(2026, 3, 5))).isFalse();
    assertThat(ParsedEventValidator.monthDayInText("2026.11.05", LocalDate.of(2026, 11, 5))).isTrue();
  }

  @Test
  void 장소는_원문과_같은_정리를_거친_뒤_비교() {
    var nbsp = event(LocalDate.of(2026, 11, 5), null, "가상\u00A0아트홀");
    assertThat(ParsedEventValidator.validate(nbsp, BLOCK).event().placeName()).isEqualTo("가상 아트홀");
  }

  @Test
  void 해_넘김_종료_연도는_원문에_없어도_인정() {
    String text = "겨울 빛 축제\n🗓 2026. 12. 28.(월) ~ 1. 3.(일)";
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3), null), text);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
  }

  @Test
  void 시작일이_없으면_NO_DATE() {
    var r = ParsedEventValidator.validate(event(null, null, "가상 아트홀"), BLOCK);
    assertThat(r.status()).isEqualTo(CandidateStatus.NO_DATE);
    assertThat(r.event().title()).isEqualTo("가을 음악회");
  }

  @Test
  void 종료가_시작보다_앞서면_FAILED() {
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 5), null), BLOCK);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void 원문에_없는_장소는_비우고_경고() {
    var r = ParsedEventValidator.validate(event(LocalDate.of(2026, 11, 5), null, "지어낸 장소"), BLOCK);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(r.event().placeName()).isNull();
    assertThat(r.warnings()).anyMatch(w -> w.contains("장소"));
  }

  @Test
  void 행사_아님은_SKIPPED_모름은_FAILED() {
    var no = new ParsedEvent(false, null, null, null, null, null, null);
    var unknown = new ParsedEvent(null, "x", null, null, null, null, null);
    assertThat(ParsedEventValidator.validate(no, BLOCK).status()).isEqualTo(CandidateStatus.SKIPPED);
    assertThat(ParsedEventValidator.validate(no, BLOCK).event()).isNull(); // 검사 안 된 값은 넘기지 않음
    assertThat(ParsedEventValidator.validate(unknown, BLOCK).status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void 모르는_카테고리는_기타_제목_없으면_FAILED() {
    var odd = new ParsedEvent(true, "가을 음악회", "아무거나", LocalDate.of(2026, 11, 5), null, null, null);
    assertThat(ParsedEventValidator.validate(odd, BLOCK).event().category()).isEqualTo("기타");
    var noTitle = new ParsedEvent(true, " ", "공연", LocalDate.of(2026, 11, 5), null, null, null);
    assertThat(ParsedEventValidator.validate(noTitle, BLOCK).status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void 긴_제목은_칸_길이에_맞춰_자름() {
    var longTitle = new ParsedEvent(true, "가".repeat(300), "공연", LocalDate.of(2026, 11, 5), null, null, null);
    assertThat(ParsedEventValidator.validate(longTitle, BLOCK).event().title()).hasSize(ParsedEventValidator.TITLE_MAX);
  }
}
