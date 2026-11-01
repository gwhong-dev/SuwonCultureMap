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

// 개인정보 처리방침이 공개되고 로그인 · 메인 · 마이페이지에서 찾을 수 있는지 (법 제30조 · 시행령 제31조)
// 낱말이 있는지만 봄: 내용이 실제 동작과 맞는지는 검증하지 않음
@SpringBootTest
@ActiveProfiles("test")
class PrivacyPolicyPageTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 로그인_없이_열리고_법정_항목을_담고_있음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/privacy.html")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains(
        "처리하는 개인정보 항목", "보유 기간", "파기", "제3자 제공", "위탁", "국외 이전",
        "권리", "법정대리인", "자동 수집 장치", "안전성 확보", "개인정보 보호책임자", "삭제 요청", "문의 메일");
  }

  @Test
  void 배포_전_입력_칸이_남아_있지_않음() throws Exception {
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).doesNotContain("배포 전 입력", "to-fill");
  }

  @Test
  void 메인_로그인_마이페이지에_링크가_있음() throws Exception {
    for (String page : new String[] { "/index.html", "/login.html", "/mypage.html" }) {
      String html = mockMvc.perform(get(page)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
      assertThat(html).as(page).contains("href=\"privacy.html\"");
    }
  }
}
