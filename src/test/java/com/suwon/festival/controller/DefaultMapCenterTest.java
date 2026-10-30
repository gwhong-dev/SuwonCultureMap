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

// 메인 화면(index.html)이 지도 기본 위치로 정조테마공연장 좌표를 쓰는지 확인
@SpringBootTest
@ActiveProfiles("test")
class DefaultMapCenterTest {

  // 재단 API가 정조테마공연장 행사에 주는 좌표 (index.html 의 DEFAULT_CENTER 와 같아야 함)
  private static final String LAT = "37.2810212008746";
  private static final String LNG = "127.016215843564";

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 지도_기본_위치는_정조테마공연장() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/index.html")).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(200);

    String html = res.getContentAsString(StandardCharsets.UTF_8);
    // 코드 모양(var/const, 공백)이 바뀌어도 깨지지 않게 "값"과 "사용 여부"만 확인
    assertThat(html).contains(LAT).contains(LNG);
    assertThat(html).contains("DEFAULT_CENTER.lat, DEFAULT_CENTER.lng");
    // 예전 기본 위치(37.2636, 127.0286)가 남아 있으면 안 됨
    assertThat(html).doesNotContain("37.2636").doesNotContain("127.0286");
  }
}
