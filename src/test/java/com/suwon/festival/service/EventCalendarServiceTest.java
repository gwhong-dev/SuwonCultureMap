package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.suwon.festival.entity.Event;

// 행사 → 캘린더 변환 규칙 확인 (DB·스프링 없이 순수 단위 테스트)
class EventCalendarServiceTest {

  private final EventCalendarService service = new EventCalendarService(null, null, null);
  private static final Instant NOW = Instant.parse("2026-10-30T14:00:00Z");

  private Event event(String title, String info, LocalDate start, LocalDate end, String link) {
    Event e = Event.builder()
        .idx(1L).title(title).category("공연")
        .latitude(37.2810212008746).longitude(127.016215843564)
        .info(info).pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(start).endDate(end).link(link)
        .build();
    ReflectionTestUtils.setField(e, "id", 7L);
    return e;
  }

  // 캘린더 B안: 장소·시간 원문이 있는 행사
  private Event eventB(LocalDate start, LocalDate end, String place, String timeText) {
    Event e = Event.builder()
        .idx(2L).title("정조 K 스테이지").category("공연")
        .latitude(37.2810212008746).longitude(127.016215843564)
        .info("시간: " + timeText).pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(start).endDate(end).place(place).timeText(timeText)
        .build();
    ReflectionTestUtils.setField(e, "id", 8L);
    return e;
  }

  private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

  @Test
  void 하루짜리_행사에_시작끝_시간이_있으면_시간_지정_일정() {
    String ics = service.toIcs(eventB(DAY, DAY, "정조테마공연장", "19:00~19:40"), NOW);
    // 한국 19:00 = UTC 10:00
    assertThat(ics).contains("DTSTART:20261107T100000Z\r\n").contains("DTEND:20261107T104000Z\r\n");
    assertThat(ics).doesNotContain("VALUE=DATE");
  }

  @Test
  void 시작_시간만_있으면_2시간_일정() {
    String ics = service.toIcs(eventB(DAY, null, "정조테마공연장", "14:00"), NOW);
    assertThat(ics).contains("DTSTART:20261107T050000Z\r\n").contains("DTEND:20261107T070000Z\r\n");
  }

  @Test
  void 여러_날_행사는_시간이_있어도_종일_유지() {
    String ics = service.toIcs(eventB(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31), "수원전통문화관", "10:00~17:00"), NOW);
    assertThat(ics).contains("DTSTART;VALUE=DATE:20260501\r\n").contains("DTEND;VALUE=DATE:20261101\r\n");
  }

  @Test
  void 여러_회차_시간은_종일_유지() {
    String ics = service.toIcs(eventB(DAY, DAY, "정조테마공연장", "14:00, 17:00"), NOW);
    assertThat(ics).contains("DTSTART;VALUE=DATE:20261107\r\n");
  }

  @Test
  void 장소명이_있으면_LOCATION_에_들어감() {
    String ics = service.toIcs(eventB(DAY, DAY, "수원전통문화관 및 수원화성 일대, 야외", null), NOW);
    assertThat(ics).contains("LOCATION:수원전통문화관 및 수원화성 일대\\, 야외\r\n"); // 쉼표는 \, 로
  }

  @Test
  void 장소명이_없으면_LOCATION_생략() {
    String ics = service.toIcs(eventB(DAY, DAY, null, null), NOW);
    assertThat(ics).doesNotContain("LOCATION:");
  }

  @Test
  void 구글도_시간_지정과_장소명을_씀() {
    String url = service.toGoogleUrl(eventB(DAY, DAY, "정조테마공연장 대공연장", "19:00~19:40"));
    assertThat(url).contains("&dates=20261107T100000Z/20261107T104000Z");
    assertThat(url).contains("&location=%EC%A0%95%EC%A1%B0"); // "정조..." 로 시작 (좌표 아님)
    assertThat(url).doesNotContain("37.281");
  }

  @Test
  void 종일_일정은_종료일_다음날로_끝남() {
    String ics = service.toIcs(event("공연", "장소: 정조테마공연장", LocalDate.of(2026, 11, 7),
        LocalDate.of(2026, 11, 18), "https://example.com/1"), NOW);

    assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
    assertThat(ics).contains("DTSTART;VALUE=DATE:20261107\r\n");
    assertThat(ics).contains("DTEND;VALUE=DATE:20261119\r\n"); // 11/18 까지 → 끝은 11/19
    assertThat(ics).contains("UID:event-7@suwonculturemap\r\n");
    assertThat(ics).contains("DTSTAMP:20261030T140000Z\r\n");
    assertThat(ics).contains("URL:https://example.com/1\r\n");
    assertThat(ics).contains("GEO:37.281021;127.016216\r\n");
    // 링크는 URL 칸에만 → 메모(DESCRIPTION)에는 "원문:" 줄이 없어야 함
    assertThat(ics).contains("DESCRIPTION:장소: 정조테마공연장\r\n").doesNotContain("원문:");
  }

  @Test
  void 종료일이_없으면_하루짜리() {
    String ics = service.toIcs(event("공연", null, LocalDate.of(2026, 11, 7), null, null), NOW);
    assertThat(ics).contains("DTEND;VALUE=DATE:20261108\r\n");
    assertThat(ics).doesNotContain("URL:");
    assertThat(ics).doesNotContain("DESCRIPTION:"); // info 도 없으면 메모 항목 생략
  }

  @Test
  void 특수문자와_줄바꿈은_규칙대로_바뀜() {
    assertThat(EventCalendarService.escape("a,b;c\\d\ne")).isEqualTo("a\\,b\\;c\\\\d\\ne");
  }

  @Test
  void 긴_줄은_75바이트_이하로_접히고_한글이_안_깨짐() {
    String longInfo = "장소: 정조테마공연장 대공연장, 시간: 매주 토요일 오후 2시와 4시 (총 2회), 문의: 수원문화재단 공연팀 ".repeat(5);
    String ics = service.toIcs(event("정조 K 스테이지", longInfo, LocalDate.of(2026, 11, 7), null, null), NOW);

    for (String line : ics.split("\r\n")) {
      assertThat(line.getBytes(StandardCharsets.UTF_8).length).as(line).isLessThanOrEqualTo(75);
      assertThat(line).doesNotContain("�"); // 깨진 글자 없음
    }
    // 접힌 줄을 다시 펴면 원래 내용과 같아야 함
    String unfolded = ics.replace("\r\n ", "");
    assertThat(unfolded).contains("DESCRIPTION:" + EventCalendarService.escape(longInfo.strip()));
  }

  @Test
  void 구글_주소는_제목_날짜_메모가_인코딩되어_들어감() {
    String url = service.toGoogleUrl(event("A+B & C", "시간: 19:30", LocalDate.of(2026, 11, 7),
        LocalDate.of(2026, 11, 18), "https://example.com/1"));

    assertThat(url).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE");
    assertThat(url).contains("&dates=20261107/20261119");
    assertThat(url).contains("&text=A%2BB%20%26%20C"); // + 와 & 가 그대로 남으면 제목이 깨짐
    assertThat(url).contains("&location=37.2810212008746,127.016215843564");
    assertThat(url).doesNotContain(" ");
  }

  @Test
  void 구글_메모는_info_빈줄_원문_접두어_링크_순서() {
    String url = service.toGoogleUrl(event("공연", "시간: 19:30", LocalDate.of(2026, 11, 7), null,
        "https://example.com/1"));
    assertThat(googleDetails(url)).isEqualTo("시간: 19:30\n\n원문: https://example.com/1");
  }

  @Test
  void 구글_메모에_info가_없으면_원문_줄만() {
    String url = service.toGoogleUrl(event("공연", null, LocalDate.of(2026, 11, 7), null,
        "https://example.com/1"));
    assertThat(googleDetails(url)).isEqualTo("원문: https://example.com/1");
  }

  // 구글 주소에서 details 값만 꺼내 디코딩
  private static String googleDetails(String url) {
    String raw = url.replaceAll(".*&details=([^&]*).*", "$1");
    return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
  }

  @Test
  void 구글_메모가_너무_길면_info만_자르고_원문_링크는_남김() {
    String url = service.toGoogleUrl(event("공연", "가".repeat(3000), LocalDate.of(2026, 11, 7), null,
        "https://example.com/1"));
    // "가" 1글자 = %EA%B0%80 (9자) → 500글자 + 나머지 여유
    assertThat(url.length()).isLessThan(500 * 9 + 600);
    assertThat(url).contains("https://example.com/1"); // 링크는 잘리지 않음
  }

  @Test
  void 이모지가_반으로_잘리지_않음() {
    String text = "가".repeat(499) + "😀" + "나";
    String cut = EventCalendarService.cut(text, 500);
    assertThat(cut).isEqualTo("가".repeat(499) + "…"); // 이모지 반쪽 대신 그 앞에서 자름
    assertThat(EventCalendarService.cut("짧음", 500)).isEqualTo("짧음");
  }

  @Test
  void 종료일이_시작일보다_빠르면_시작일_하루짜리() {
    String ics = service.toIcs(event("공연", null, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 1), null), NOW);
    assertThat(ics).contains("DTEND;VALUE=DATE:20261108\r\n");
  }

  @Test
  void 링크에_줄바꿈이_섞이면_URL_항목을_뺌() {
    String ics = service.toIcs(event("공연", null, LocalDate.of(2026, 11, 7), null,
        "https://example.com/1\r\nATTENDEE:mailto:x@y.z"), NOW);
    assertThat(ics).doesNotContain("URL:").doesNotContain("\r\nATTENDEE");
    assertThat(EventCalendarService.safeLink("  https://example.com/a?b=1  ")).isEqualTo("https://example.com/a?b=1");
    assertThat(EventCalendarService.safeLink("javascript:alert(1)")).isNull();
  }

  // ===== 진행 날짜 고르기 (docs/event-schedule.md 7번) =====

  @Test
  void 고른_날짜는_그날_하루_종일_일정() {
    Event e = event("정조 K 스테이지", "", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 9, 19), null);
    String ics = service.toIcs(e, LocalDate.of(2026, 9, 19), NOW);

    assertThat(ics).contains("DTSTART;VALUE=DATE:20260919\r\n").contains("DTEND;VALUE=DATE:20260920\r\n");
    assertThat(ics).contains("UID:event-7-20260919@suwonculturemap");
    assertThat(service.toGoogleUrl(e, LocalDate.of(2026, 9, 19))).contains("&dates=20260919/20260920&");
  }

  @Test
  void 고른_날짜에_확실한_시간이_있으면_시간_지정() {
    Event e = eventB(LocalDate.of(2026, 5, 16), LocalDate.of(2026, 9, 19), "정조테마공연장", "19:00~19:40");
    // 여러 날 행사라 날짜를 안 고르면 지금처럼 종일
    assertThat(service.toIcs(e, NOW)).contains("DTSTART;VALUE=DATE:20260516");
    // 한국 5/16 19:00 = UTC 10:00
    String ics = service.toIcs(e, LocalDate.of(2026, 5, 16), NOW);
    assertThat(ics).contains("DTSTART:20260516T100000Z").contains("DTEND:20260516T104000Z");
    assertThat(service.toGoogleUrl(e, LocalDate.of(2026, 5, 16))).contains("&dates=20260516T100000Z/20260516T104000Z&");
  }

  @Test
  void 날짜를_안_고르면_UID_와_기간이_그대로() {
    Event e = event("정조 K 스테이지", "", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 9, 19), null);
    assertThat(service.toIcs(e, null, NOW)).isEqualTo(service.toIcs(e, NOW)).contains("UID:event-7@suwonculturemap");
  }

  // 실데이터(11/10 운영 155건)에서 날짜 · 요일마다 시간이 다른 진행 날짜 행사의 시간 원문
  // → 고른 날에 엉뚱한 시간을 넣지 않고 종일로 둠 (PR #72 리뷰 확인)
  @Test
  void 날마다_시간이_다른_원문이면_고른_날짜도_종일() {
    String[] texts = {
        "화 ~ 토요일 09:30~21:30(입장 마감 21:00)\n일요일 09:30~17:30(입장 마감 17:00)\n월요일휴관",
        "(토) 17:00~18:00 (일) 14:00~15:00 (60분)",
        "5.8.(금) 19:30\n5.9.(토) 14:00, 17:00",
        "12.24.(수) 19:30 ㅣ 12.25.(목) 14:00, 17:00",
        "(낮코스)13:30~14:30 (밤코스)18:30~19:30",
        "화요일 ~ 일요일 10:00 ~ 17:00  (월요일 및 공휴일 미운영)",
    };
    for (String t : texts) {
      Event e = eventB(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 9), "정조테마공연장", t);
      assertThat(service.toIcs(e, LocalDate.of(2026, 5, 9), NOW)).as(t)
          .contains("DTSTART;VALUE=DATE:20260509\r\n").contains("DTEND;VALUE=DATE:20260510\r\n");
    }
  }

  @Test
  void 긴_행사는_시작_종료일_포함_30일_초과() {
    LocalDate start = LocalDate.of(2026, 11, 1);
    assertThat(EventCalendarService.isLongEvent(event("t", "", start, LocalDate.of(2026, 11, 29), null))).isFalse(); // 29일
    assertThat(EventCalendarService.isLongEvent(event("t", "", start, LocalDate.of(2026, 11, 30), null))).isFalse(); // 30일
    assertThat(EventCalendarService.isLongEvent(event("t", "", start, LocalDate.of(2026, 12, 1), null))).isTrue(); // 31일
    assertThat(EventCalendarService.isLongEvent(event("t", "", start, null, null))).isFalse();
    assertThat(EventCalendarService.isLongEvent(event("t", "", start, LocalDate.of(2026, 10, 1), null))).isFalse(); // 뒤바뀜
  }

  @Test
  void 긴_행사도_고른_날에_시간이_있으면_그날_시간_지정() {
    LocalDate pick = LocalDate.of(2026, 11, 15);
    String ics = service.toIcs(eventB(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 28), "수원시립미술관", "10:00~18:00"), pick, NOW);
    assertThat(ics).contains("DTSTART:20261115T010000Z\r\n").contains("DTEND:20261115T090000Z\r\n");
  }
}
