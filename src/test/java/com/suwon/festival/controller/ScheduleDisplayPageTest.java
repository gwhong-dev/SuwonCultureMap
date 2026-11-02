package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 메인 화면의 진행 날짜 표시가 연결되어 있는지 확인 (표시 규칙 자체는 브라우저에서 실제 데이터로 확인)
@SpringBootTest
@ActiveProfiles("test")
class ScheduleDisplayPageTest {

  @Autowired private WebApplicationContext context;

  private String page() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void 목록_미리보기_상세가_같은_표시_함수를_씀() throws Exception {
    String html = page();

    assertThat(html).contains("var dateText = formatEventDates(item.event)");
    assertThat(html).contains("escapeHtml(formatEventDates(item.event))");
    assertThat(html).contains("document.getElementById(\"modalDate\").textContent = formatEventDates(event)");
  }

  @Test
  void 상세에_일정_칸과_공휴일_안내가_있음() throws Exception {
    String html = page();

    assertThat(html).contains("id=\"modalSchedule\"", "공휴일은 원문 확인", "일정 원문: ");
  }
}
