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
}
