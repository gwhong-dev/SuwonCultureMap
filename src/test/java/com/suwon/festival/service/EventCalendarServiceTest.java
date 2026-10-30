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

  private final EventCalendarService service = new EventCalendarService(null);
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
}
