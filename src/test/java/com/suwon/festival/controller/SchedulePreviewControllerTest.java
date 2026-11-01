package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.service.EventSyncService;

// GET /api/events/schedule/preview 응답 확인 (test 프로필, 재단 호출은 가짜)
@SpringBootTest
@ActiveProfiles("test")
class SchedulePreviewControllerTest {

  @Autowired private WebApplicationContext context;
  @MockitoBean private EventSyncService eventSyncService;

  @Test
  void 추출_결과를_JSON_으로_돌려줌() throws Exception {
    when(eventSyncService.fetchRawXml()).thenReturn("<rss><channel><item><idx>1</idx><title>미리보기 행사</title>"
        + "<eventDate><sdate>2026-05-16</sdate><edate>2026-09-19</edate></eventDate>"
        + "<description><p>행사일정 : 2026. 5. 16.(토) / 9. 19.(토) 총 2회</p></description>"
        + "</item></channel></rss>");

    MockHttpServletResponse res = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(get("/api/events/schedule/preview")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentType()).startsWith("application/json");
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"total\":1", "미리보기 행사", "\"status\":\"DATES\"", "2026-09-19");
  }
}
