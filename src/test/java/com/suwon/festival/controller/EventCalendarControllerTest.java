package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;

// /api/events/{id}/calendar.ics · /calendar/google 응답 확인 (test 프로필, H2)
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 테스트마다 저장한 행사를 되돌림
class EventCalendarControllerTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private EventRepository eventRepository;

  @Autowired
  private EventDateRepository eventDateRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  private Event save(Long idx, LocalDate start) {
    return eventRepository.save(Event.builder()
        .idx(idx).title("정조 K 스테이지").category("공연")
        .latitude(37.2810212008746).longitude(127.016215843564)
        .info("장소: 정조테마공연장").pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(start).endDate(start)
        .build());
  }

  @Test
  void ics_파일로_응답() throws Exception {
    Event e = save(900001L, LocalDate.of(2026, 11, 7));

    MockHttpServletResponse res = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar.ics"))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentType()).startsWith("text/calendar");
    assertThat(res.getHeader("Content-Disposition")).contains(".ics");
    String body = res.getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).contains("SUMMARY:정조 K 스테이지").contains("DTSTART;VALUE=DATE:20261107");
  }

  @Test
  void 구글_캘린더로_이동() throws Exception {
    Event e = save(900002L, LocalDate.of(2026, 11, 7));

    MockHttpServletResponse res = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar/google"))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(302);
    assertThat(res.getHeader("Location"))
        .startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
        .contains("dates=20261107/20261108");
  }

  @Test
  void 없는_행사는_404() throws Exception {
    assertThat(mockMvc.perform(get("/api/events/999999999/calendar.ics")).andReturn().getResponse().getStatus())
        .isEqualTo(404);
  }

  @Test
  void 날짜_없는_행사는_404() throws Exception {
    Event e = save(900003L, null);
    assertThat(mockMvc.perform(get("/api/events/" + e.getId() + "/calendar/google")).andReturn().getResponse()
        .getStatus()).isEqualTo(404);
  }

  // 5/16 · 9/19 두 번만 하는 행사 (idx 2972 와 같은 모양)
  private Event saveRunDates(Long idx) {
    Event e = eventRepository.save(Event.builder()
        .idx(idx).title("정조 K 스테이지").category("공연")
        .latitude(37.2810212008746).longitude(127.016215843564)
        .info("장소: 정조테마공연장").pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(LocalDate.of(2026, 5, 16)).endDate(LocalDate.of(2026, 9, 19))
        .build());
    e.updateSchedule(Event.SCHEDULE_DATES, "5. 16.(토), 9. 19.(토)", false);
    eventDateRepository.save(new EventDate(e.getId(), LocalDate.of(2026, 5, 16)));
    eventDateRepository.save(new EventDate(e.getId(), LocalDate.of(2026, 9, 19)));
    return e;
  }

  private int status(String url) throws Exception {
    return mockMvc.perform(get(url)).andReturn().getResponse().getStatus();
  }

  @Test
  void 진행_날짜를_고르면_그날_일정() throws Exception {
    Event e = saveRunDates(900004L);

    MockHttpServletResponse ics = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar.ics?date=2026-09-19"))
        .andReturn().getResponse();
    assertThat(ics.getStatus()).isEqualTo(200);
    assertThat(ics.getHeader("Content-Disposition")).contains("-2026-09-19.ics");
    assertThat(ics.getContentAsString(StandardCharsets.UTF_8))
        .contains("DTSTART;VALUE=DATE:20260919").contains("DTEND;VALUE=DATE:20260920");

    MockHttpServletResponse google = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar/google?date=2026-05-16"))
        .andReturn().getResponse();
    assertThat(google.getStatus()).isEqualTo(302);
    assertThat(google.getHeader("Location")).contains("dates=20260516/20260517");
  }

  @Test
  void 진행하지_않는_날이나_기간_밖_날짜는_400() throws Exception {
    Event e = saveRunDates(900005L);
    assertThat(status("/api/events/" + e.getId() + "/calendar.ics?date=2026-05-23")).isEqualTo(400); // 기간 안 · 진행 안 함
    assertThat(status("/api/events/" + e.getId() + "/calendar/google?date=2026-12-01")).isEqualTo(400); // 기간 밖
  }

  @Test
  void 날짜_모르는_행사에_날짜를_보내면_400() throws Exception {
    Event e = save(900006L, LocalDate.of(2026, 11, 7));
    assertThat(status("/api/events/" + e.getId() + "/calendar.ics?date=2026-11-07")).isEqualTo(400);
    assertThat(status("/api/events/" + e.getId() + "/calendar.ics")).isEqualTo(200); // 날짜 없으면 지금과 같음
  }

  @Test
  void 날짜_형식이_틀리면_400() throws Exception {
    Event e = saveRunDates(900007L);
    assertThat(status("/api/events/" + e.getId() + "/calendar.ics?date=2026-13-01")).isEqualTo(400);
    assertThat(status("/api/events/" + e.getId() + "/calendar/google?date=abc")).isEqualTo(400);
  }

  // ===== 기간이 30일을 넘는 긴 행사(상설 등): 고른 하루 (11/11 사용자 결정) =====
  private Event savePeriod(Long idx, LocalDate start, LocalDate end) {
    return eventRepository.save(Event.builder()
        .idx(idx).title("상설 전시").category("전시")
        .latitude(37.2810212008746).longitude(127.016215843564)
        .info("장소: 수원시립미술관").pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(start).endDate(end)
        .build());
  }

  @Test
  void 긴_행사는_기간_안_고른_하루_일정() throws Exception {
    Event e = savePeriod(900008L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)); // 31일

    MockHttpServletResponse google = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar/google?date=2026-11-15"))
        .andReturn().getResponse();
    assertThat(google.getStatus()).isEqualTo(302);
    assertThat(google.getHeader("Location")).contains("dates=20261115/20261116");

    MockHttpServletResponse ics = mockMvc.perform(get("/api/events/" + e.getId() + "/calendar.ics?date=2026-12-01"))
        .andReturn().getResponse();
    assertThat(ics.getStatus()).isEqualTo(200);
    assertThat(ics.getContentAsString(StandardCharsets.UTF_8))
        .contains("DTSTART;VALUE=DATE:20261201").contains("DTEND;VALUE=DATE:20261202");

    assertThat(status("/api/events/" + e.getId() + "/calendar.ics?date=2026-10-31")).isEqualTo(400); // 기간 밖
    assertThat(status("/api/events/" + e.getId() + "/calendar/google?date=2026-12-02")).isEqualTo(400);
    assertThat(status("/api/events/" + e.getId() + "/calendar.ics")).isEqualTo(200); // 날짜 없으면 기간 전체 (예전 링크)
  }

  @Test
  void 기간이_30일_이하면_날짜를_보내도_400() throws Exception {
    Event d30 = savePeriod(900009L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)); // 30일
    Event d29 = savePeriod(900010L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 29)); // 29일
    assertThat(status("/api/events/" + d30.getId() + "/calendar.ics?date=2026-11-15")).isEqualTo(400);
    assertThat(status("/api/events/" + d29.getId() + "/calendar/google?date=2026-11-15")).isEqualTo(400);
  }
}
