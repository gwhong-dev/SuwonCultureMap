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

// 메인 화면의 진행 날짜 표시가 연결되어 있는지 확인 (표시 규칙 자체는 src/test/js 에서 시험)
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

    // 목록 · 미리보기는 날짜 필터의 "기간 중 · 일정 확인" 을 붙이는 cardDateText 를 거침
    assertThat(html).contains("var text = formatEventDates(item.event)");
    assertThat(html).contains("var dateText = cardDateText(item)");
    assertThat(html).contains("escapeHtml(cardDateText(item))");
    assertThat(html).contains("document.getElementById(\"modalDate\").textContent = formatEventDates(event)");
  }

  @Test
  void 날짜_필터가_목록_필터에_연결됨() throws Exception {
    String html = page();

    assertThat(html).contains("<select id=\"dateFilter\"", "<option value=\"today\">", "<option value=\"weekend\">");
    assertThat(html).contains("filterDays(document.getElementById(\"dateFilter\").value)");
    assertThat(html).contains("matchRunDays(item.event, runDays)");
  }

  @Test
  void 상세에_일정_칸이_있고_표시_규칙_파일을_불러옴() throws Exception {
    assertThat(page()).contains("id=\"modalSchedule\"", "<script src=\"/schedule-format.js\"></script>");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/schedule-format.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }
}
