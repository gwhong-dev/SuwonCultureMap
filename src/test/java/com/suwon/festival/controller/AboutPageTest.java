package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 서비스 이름 · 비공식 표기 · 삭제 요청 창구가 공개되는지
@SpringBootTest
@ActiveProfiles("test")
class AboutPageTest {

  private static final String[] PAGES = { "/index.html", "/login.html", "/mypage.html", "/privacy.html" };

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 로그인_없이_열리고_비공식_표기와_문의_창구를_담고_있음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/about.html")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("비공식", "수원문화재단", "삭제", "contact@suwonbible.com");
  }

  @Test
  void 인스타그램은_공식_계정만_출처로_안내함() throws Exception {
    String html = mockMvc.perform(get("/about.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).contains("수원문화재단 공개 API", "공식 인스타그램 계정", "원문 링크");
    assertThat(html).doesNotContain("수집을 중단"); // 중단 안내 문구
  }

  @Test
  void 모든_화면에_새_이름과_안내_링크가_있고_옛_이름이_없음() throws Exception {
    for (String page : PAGES) {
      String html = mockMvc.perform(get(page)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
      assertThat(html).as(page).contains("수원바이블", "href=\"about.html\"").doesNotContain("수원 문화 프로그램 지도");
    }
  }
}
