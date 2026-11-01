package com.suwon.festival.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.schedule.ScheduleResult.Status;

// 진행 날짜 추출 — 문구는 재단 XML 실데이터 (2026-11-01 조사, docs/event-schedule.md 2 · 3번). idx 는 재단 행사 번호
class ScheduleExtractorTest {

  private static LocalDate d(int year, int month, int day) {
    return LocalDate.of(year, month, day);
  }

  private static ScheduleResult run(LocalDate start, LocalDate end, String time, String weekday, String body) {
    return ScheduleExtractor.extract(new ScheduleInput(start, end, time, weekday, body));
  }

  // ===== ① 하루짜리 =====

  @Test
  void 시작일과_종료일이_같으면_그_하루() {
    ScheduleResult r = run(d(2026, 11, 14), d(2026, 11, 14), "14:00~15:00 (60분)", null, null);
    assertThat(r.status()).isEqualTo(Status.DATES);
    assertThat(r.dates()).containsExactly(d(2026, 11, 14));
  }

  @Test
  void 종료일이_없으면_시작일_하루() {
    assertThat(run(d(2026, 11, 14), null, null, null, null).dates()).containsExactly(d(2026, 11, 14));
  }

  @Test
  void 시작일이_없거나_종료일이_앞서면_모름() {
    assertThat(run(null, d(2026, 11, 14), null, null, null).status()).isEqualTo(Status.UNKNOWN);
    assertThat(run(d(2026, 11, 14), d(2026, 11, 1), null, null, null).status()).isEqualTo(Status.UNKNOWN);
  }

  // ===== ② 날짜 나열 =====

  @Test
  void 토요일_고정_표기여도_본문의_날짜가_정답_idx2972() {
    ScheduleResult r = run(d(2026, 5, 16), d(2026, 9, 19), "토요일 14:00 ~ 17:30  (월요일 및 공휴일 미운영)", null,
        "행사일정 : 2026. 5. 16.(토) / 9. 19.(토) 총 2회\n행사시간 : 14:00");
    assertThat(r.status()).isEqualTo(Status.DATES);
    assertThat(r.dates()).containsExactly(d(2026, 5, 16), d(2026, 9, 19)); // 토요일 19일이 아니라 2일
    assertThat(r.holidayNote()).isFalse();
  }

  @Test
  void 시간_칸의_특정_날짜_여러_줄_idx2955() {
    ScheduleResult r = run(d(2026, 4, 29), d(2026, 10, 24),
        "4.29.(수) 11:00 / 5.27.(수) 11:00 / 6.24.(수) 11:00/\n8.22.(토) 17:00 / 9.16.(수) 11:00 / 10.24.(토) 17:00", null, null);
    assertThat(r.dates()).hasSize(6).startsWith(d(2026, 4, 29)).endsWith(d(2026, 10, 24));
  }

  @Test
  void 시간칸이_시간뿐이어도_본문_운영일로_읽음_idx3051() {
    ScheduleResult r = run(d(2026, 10, 31), d(2026, 11, 22), "13:00~19:00", null,
        "운영일 : 2026. 10. 31.(토), 11. 1.(일), 11. 7.(토), 11. 8.(일), 11. 21.(토), 11. 22.(일)\n모집인원 : 회차별 20명");
    assertThat(r.dates()).hasSize(6).contains(d(2026, 11, 1)).doesNotContain(d(2026, 11, 14));
    assertThat(r.sourceText()).startsWith("운영일");
  }

  @Test
  void 시간의_물결표는_날짜_범위가_아님_idx2845() {
    ScheduleResult r = run(d(2025, 11, 29), d(2025, 12, 6), "10:00~13:00", null,
        "일 시 : 2025. 11. 29.(토) 12. 6.(토) 10:00~13:00");
    assertThat(r.dates()).containsExactly(d(2025, 11, 29), d(2025, 12, 6));
  }

  @Test
  void 해를_넘기는_나열은_기간에_맞는_해로_읽음() {
    ScheduleResult r = run(d(2026, 12, 26), d(2027, 1, 2), null, null, "일정 : 12. 26.(토) / 1. 2.(토)");
    assertThat(r.dates()).containsExactly(d(2026, 12, 26), d(2027, 1, 2));
  }

  @Test
  void 취소된_날은_빼고_취소_안내문은_무시() {
    ScheduleResult r = run(d(2026, 10, 1), d(2026, 11, 1), null, null, "일정 : 10. 11.(일) / 10. 18.(일) 우천예보 취소 / 11. 1.(일)");
    assertThat(r.dates()).containsExactly(d(2026, 10, 11), d(2026, 11, 1));

    ScheduleResult notice = run(d(2026, 10, 1), d(2026, 11, 1), null, null, "일정 : 10. 11.(일) / 10. 18.(일) 우천 시 취소될 수 있음");
    assertThat(notice.dates()).containsExactly(d(2026, 10, 11), d(2026, 10, 18));
  }

  // ===== ③ 매주 요일 =====

  @Test
  void 요일_칸의_매주_범위와_공휴일_표시_idx2970() {
    ScheduleResult r = run(d(2026, 5, 1), d(2026, 11, 1), "18:00~21:30", "기간 중 매주 금~일요일, 공휴일", null);
    assertThat(r.status()).isEqualTo(Status.DATES);
    assertThat(r.dates()).contains(d(2026, 5, 1), d(2026, 5, 2), d(2026, 5, 3), d(2026, 11, 1)).doesNotContain(d(2026, 5, 4));
    assertThat(r.dates()).allMatch(x -> x.getDayOfWeek().getValue() >= DayOfWeek.FRIDAY.getValue());
    assertThat(r.holidayNote()).isTrue();
  }

  @Test
  void 토요일의_일을_일요일로_읽지_않음() {
    ScheduleResult r = run(d(2026, 11, 1), d(2026, 11, 30), null, "매주 토요일", null);
    assertThat(r.dates()).allMatch(x -> x.getDayOfWeek() == DayOfWeek.SATURDAY).hasSize(4);
  }

  @Test
  void 본문의_매주와_기간_한_줄_idx2995() {
    ScheduleResult r = run(d(2026, 8, 8), d(2026, 8, 29), "15:00, 18:00", null, "기간 : 2026. 8. 1.(토) ~ 8. 29.(토) 기간 중 매주 토요일");
    assertThat(r.dates()).containsExactly(d(2026, 8, 8), d(2026, 8, 15), d(2026, 8, 22), d(2026, 8, 29)); // 기간은 XML 기준
  }

  @Test
  void 슬래시로_쓴_요일_idx2935() {
    ScheduleResult r = run(d(2026, 4, 4), d(2026, 4, 26), null, null, "일정 : 2026. 4. 4.(토) ~ 4. 26.(일) 매주 토/일요일");
    assertThat(r.dates()).hasSize(8);
  }

  @Test
  void 달_한정_매주_범위_idx3043() {
    ScheduleResult r = run(d(2026, 9, 4), d(2026, 10, 31), null, null, "운영일시 : 9월~10월 매주 금,토 18:00~21:00");
    assertThat(r.dates()).hasSize(18);
  }

  @Test
  void 달_한정_매주_나열_idx2962() {
    ScheduleResult r = run(d(2026, 5, 1), d(2026, 10, 31), null, null, "진행일시 l 5·6·9·10월 매주 금·토·일");
    assertThat(r.dates()).contains(d(2026, 6, 5), d(2026, 9, 4)).doesNotContain(d(2026, 7, 3), d(2026, 8, 7));
  }

  @Test
  void 연도가_붙은_달_범위_idx3031() {
    ScheduleResult r = run(d(2026, 8, 1), d(2026, 11, 1), null, null, "기간 : 2026년 8~11월 매주 금·토·일요일");
    assertThat(r.dates()).startsWith(d(2026, 8, 1)).endsWith(d(2026, 11, 1)).doesNotContain(d(2026, 8, 3));
  }

  // ===== ④ 요일 범위 · 쉬는 요일 =====

  @Test
  void 요일_범위와_쉬는_요일_idx3044() {
    ScheduleResult r = run(d(2026, 9, 1), d(2026, 9, 30), "화요일 ~ 일요일 10:00 ~ 17:00  (월요일 및 공휴일 미운영)", null, null);
    assertThat(r.dates()).hasSize(26).doesNotContain(d(2026, 9, 7));
    assertThat(r.holidayNote()).isTrue();
  }

  @Test
  void 쉬는_요일만_있는_시간칸_idx3034() {
    ScheduleResult r = run(d(2026, 8, 14), d(2026, 8, 23), "10:00~17:00 (야행기간 10:00~22:00 / 월 휴관)", null, null);
    assertThat(r.dates()).hasSize(9).doesNotContain(d(2026, 8, 17));
    assertThat(r.holidayNote()).isFalse();
  }

  @Test
  void 요일별_시간표에_쉬는_요일이_같이_있으면_쉬는_요일_기준_idx3038() {
    ScheduleResult r = run(d(2026, 8, 14), d(2026, 8, 30),
        "화 ~ 토요일 09:30~21:30(입장 마감 21:00)\n일요일 09:30~17:30(입장 마감 17:00)\n월요일휴관", null, null);
    assertThat(r.dates()).contains(d(2026, 8, 16)).doesNotContain(d(2026, 8, 17)); // 일요일 진행, 월요일 휴관
  }

  @Test
  void 매주_월요일_휴관은_매주_진행이_아니라_쉬는_요일_idx2984() {
    ScheduleResult r = run(d(2026, 6, 9), d(2026, 10, 5), "10:00~18:00(17:00 입장 마감)", null,
        "기간 : 2026. 6. 9.(화) ~ 10. 5.(월) (매주 월요일 휴관 / 10월 5일의 경우 대체공휴일로 개관)");
    assertThat(r.dates()).contains(d(2026, 6, 9)).doesNotContain(d(2026, 6, 15));
  }

  @Test
  void 본문_관람시간의_요일_범위와_별도_줄의_공휴일_안내_idx2966() {
    ScheduleResult r = run(d(2026, 5, 6), d(2026, 5, 31), null, null,
        "관람시간 : 10:00 ~ 17:00 (화요일 ~ 일요일)\n※ 월요일 및 법정공휴일은 전시실을 운영하지 않습니다.");
    assertThat(r.dates()).doesNotContain(d(2026, 5, 11));
    assertThat(r.holidayNote()).isTrue();
  }

  // ===== ⑤ 매일 =====

  @Test
  void 허용_라벨_줄에_매일이_적히면_기간_전체() {
    ScheduleResult r = run(d(2026, 9, 1), d(2026, 9, 3), null, null, "운영일시 : 매일 18:00~22:00");
    assertThat(r.dates()).hasSize(3);
  }

  @Test
  void 안내문의_매일은_읽지_않음_idx3011() {
    ScheduleResult r = run(d(2026, 7, 11), d(2026, 10, 31), null, null, "* 수원미디어아트 기간동안은 장안공원에서 매일 18시~22시 운영됩니다.");
    assertThat(r.status()).isEqualTo(Status.UNKNOWN);
  }

  // ===== 일부러 못 읽게 두는 경우 (설계 3번) =====

  private void assertUnknown(LocalDate start, LocalDate end, String time, String weekday, String body) {
    ScheduleResult r = run(start, end, time, weekday, body);
    assertThat(r.status()).isEqualTo(Status.UNKNOWN);
    assertThat(r.dates()).isEmpty();
    assertThat(r.reason()).isNotBlank();
  }

  @Test
  void 시간만_있으면_매일로_단정하지_않음_idx3066() {
    assertUnknown(d(2026, 10, 23), d(2026, 11, 15), "13:00~18:00", null, "전시기간 : 2026. 10. 23.(금) ~ 11. 15.(일)\n관람시간 : 13:00~18:00");
  }

  @Test
  void 요일_고정만으로는_펼치지_않음() {
    assertUnknown(d(2026, 5, 16), d(2026, 9, 19), "토요일 14:00 ~ 17:30  (월요일 및 공휴일 미운영)", null, null);
  }

  @Test
  void 요일이_나와도_시간_조건이면_못_읽음_idx2981() {
    assertUnknown(d(2026, 6, 25), d(2026, 6, 28), "11:00~19:30 (일요일 18:00 종료)", null, null);
    assertUnknown(d(2026, 9, 4), d(2026, 9, 6), "14:00~21:00\n(일요일은 20:00까지)", null, null);
  }

  @Test
  void 달별_시간표의_월은_월요일이_아님_idx3011() {
    assertUnknown(d(2026, 7, 11), d(2026, 10, 31), "9월 16:00~21:00 / 10월 15:00~20:00", null, null);
  }

  @Test
  void 도중에_요일이_바뀌면_모름_idx2953() {
    assertUnknown(d(2026, 4, 8), d(2026, 10, 31), null, null, "체험요일 : 매주 수요일, 토요일 ※ 2026년 9월부터 목요일, 토요일 운영 예정");
  }

  @Test
  void 매주_문구와_회차_표가_어긋나면_모름_idx2975() {
    assertUnknown(d(2026, 5, 1), d(2026, 10, 31), "19:00", null,
        "기간 : 2026. 5. 1.(금) ~ 10. 31.(토) 기간 중 매주 금, 토요일\n10월 공연일정\n10. 3.(토) 19:00 |\n10. 17.(토) 19:00 |\n10. 24.(토) 19:00 |\n10. 31.(토) 19:00 |");
  }

  @Test
  void 매주_문구와_회차_표가_맞으면_통과() {
    ScheduleResult r = run(d(2026, 10, 1), d(2026, 10, 31), null, null,
        "기간 : 기간 중 매주 토요일\n10. 3.(토) 19:00 |\n10. 10.(토) 19:00 |\n10. 17.(토) 19:00 |\n10. 24.(토) 19:00 |\n10. 31.(토) 19:00 |");
    assertThat(r.dates()).hasSize(5);
  }

  @Test
  void 라벨이_틀린_줄은_안_읽음_idx2934() {
    assertUnknown(d(2026, 3, 22), d(2026, 6, 23), "종일", null, "전시장소 : 3. 23.(월) ~ 6. 22.(월)");
  }

  @Test
  void 접수_예매_줄의_날짜는_안_읽음() {
    assertUnknown(d(2026, 9, 11), d(2026, 10, 23), "19:30", null,
        "티켓오픈 : 2026. 8. 7.(금) 14:00 / 2026. 8. 8.(토) 14:00\n접수일시 : 2026. 9. 1.(화) 10:00 / 9. 2.(수) 10:00");
  }

  @Test
  void 라벨_없는_연계_프로그램_날짜는_안_읽음_idx3066() {
    assertUnknown(d(2026, 10, 23), d(2026, 11, 15), null, null, "2026. 10. 31.(토), 11. 1.(일)\n2026. 10. 24.(토), 11. 7.(토), 11. 14.(토)");
  }

  @Test
  void 본문_나열_줄이_서로_다르면_고르지_않음() {
    assertUnknown(d(2026, 11, 1), d(2026, 11, 30), null, null, "일시: 11. 26.(목) 15:00 / 11. 28.(토) 14:00\n일시: 11. 7.(토) 14:00 / 11. 14.(토) 14:00");
  }

  @Test
  void 날짜_범위_한_줄은_나열이_아님() {
    assertUnknown(d(2026, 10, 4), d(2026, 10, 11), null, null, "해설기간 : 2026. 10. 4.(일) ~ 10. 11.(일), 단 8일간");
  }

  @Test
  void 줄표로_쓴_날짜_범위도_나열이_아님_idx2956() {
    assertUnknown(d(2026, 4, 14), d(2026, 6, 14), null, null, "전시 기간: 2026. 4. 14.(화) - 6. 14.(일)");
    ScheduleResult r = run(d(2026, 4, 14), d(2026, 6, 14), "10:00~18:00  *17:00 입장 마감", null,
        "전시 기간: 2026. 4. 14.(화) – 6. 14.(일)\n관람 시간: 화~일요일 10:00~18:00");
    assertThat(r.dates()).contains(d(2026, 4, 14), d(2026, 6, 14)).doesNotContain(d(2026, 4, 20)); // 4/20 월요일
  }

  // ===== 검증 (설계 4번) — 하나라도 틀리면 전체 버림 =====

  @Test
  void 요일이_달력과_다르면_전체_버림() {
    assertUnknown(d(2026, 5, 1), d(2026, 6, 30), null, null, "일정 : 5. 16.(토) / 5. 29.(목)"); // 5/29 는 금요일
  }

  @Test
  void 기간_밖_날짜가_있으면_전체_버림() {
    assertUnknown(d(2026, 5, 16), d(2026, 9, 19), null, null, "일정 : 2026. 5. 16.(토) / 2026. 10. 3.(토)");
  }

  @Test
  void 총_횟수와_다르면_버림_idx2958() {
    // 5월 · 9월 토요일은 달력상 9일인데 원문은 총 8회
    assertUnknown(d(2026, 5, 2), d(2026, 11, 8), null, null, "진행일시 l 5월 / 9월 매주 토요일 09:30 ~ 14:30 (5시간 소요) [총 8회 운영]");
  }

  @Test
  void 없는_날짜와_윤년() {
    assertUnknown(d(2026, 2, 1), d(2026, 3, 31), null, null, "일정 : 2. 29.(일) / 3. 1.(일)");
    assertThat(run(d(2028, 2, 1), d(2028, 3, 31), null, null, "일정 : 2. 29.(화) / 3. 1.(수)").dates())
        .containsExactly(d(2028, 2, 29), d(2028, 3, 1));
  }

  @Test
  void 기간_안에_해당_요일이_없으면_모름() {
    assertUnknown(d(2026, 11, 2), d(2026, 11, 3), null, "기간 중 매주 토요일", null); // 월 · 화
  }

  @Test
  void 결과_목록은_날짜순이고_중복이_없음() {
    ScheduleResult r = run(d(2026, 9, 1), d(2026, 9, 30), null, null, "일정 : 9. 19.(토) / 9. 5.(토) / 9. 5.(토)");
    assertThat(r.dates()).isEqualTo(List.of(d(2026, 9, 5), d(2026, 9, 19)));
  }
}
