package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;

// GET /api/events/merge/preview 응답 확인 (test 프로필, H2)
@SpringBootTest(properties = "instagram.sync.enabled=true") // 재단 · 인스타 묶음이 있어야 응답 모양을 볼 수 있음
@ActiveProfiles("test")
@Transactional
class EventMergePreviewControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private EventRepository eventRepository;

  @Test
  void 합칠_묶음을_JSON_으로_돌려줌() throws Exception {
    eventRepository.saveAndFlush(event("합치기응답 재단 공연").idx(71001L).build());
    eventRepository.saveAndFlush(event("합치기응답 재단 공연")
        .source(Event.SOURCE_INSTAGRAM).sourceKey("합치기응답#1").build());

    MockHttpServletResponse res = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(get("/api/events/merge/preview")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentType()).startsWith("application/json");
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"groupCount\"", "\"representative\"", "\"hidden\"", "합치기응답 재단 공연");
  }

  private static Event.EventBuilder event(String title) {
    return Event.builder()
        .title(title).category("공연").latitude(37.2).longitude(126.8).place("합치기응답 홀")
        .pubDate(LocalDateTime.of(2032, 1, 1, 0, 0))
        .startDate(LocalDate.of(2032, 2, 2)).endDate(LocalDate.of(2032, 2, 2));
  }
}
