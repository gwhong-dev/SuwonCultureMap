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

// 메인 화면 스크립트(js/index.js)가 지도 기본 위치로 화성행궁 광장(행궁동 중심) 좌표를 쓰는지 확인
@SpringBootTest
@ActiveProfiles("test")
class DefaultMapCenterTest {

  // 재단 API가 화성행궁 광장 행사에 주는 좌표 (js/index.js 의 DEFAULT_CENTER 와 같아야 함)
  private static final String LAT = "37.2818820191942";
  private static final String LNG = "127.014394463798";

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 지도_기본_위치는_화성행궁_광장() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/js/index.js")).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(200);

    String js = res.getContentAsString(StandardCharsets.UTF_8);
    // 코드 모양(var/const, 공백)이 바뀌어도 깨지지 않게 "값"과 "사용 여부"만 확인
    assertThat(js).contains(LAT).contains(LNG);
    assertThat(js).contains("DEFAULT_CENTER.lat, DEFAULT_CENTER.lng");
    // 예전 기본 위치(37.2636, 127.0286 · 정조테마공연장 37.2810212008746)가 남아 있으면 안 됨
    assertThat(js).doesNotContain("37.2636").doesNotContain("127.0286").doesNotContain("37.2810212008746");
  }
}
