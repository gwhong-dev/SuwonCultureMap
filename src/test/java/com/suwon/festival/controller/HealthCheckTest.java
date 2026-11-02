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

// /actuator/health: 배포 스크립트 · CI 가 앱 기동 확인에 씀. 상태만 공개하고 다른 actuator 는 닫혀 있어야 함
@SpringBootTest
@ActiveProfiles("test")
class HealthCheckTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 헬스체크는_상태만_돌려줌() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/actuator/health")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"UP\"}");
  }

  @Test
  void 다른_actuator_엔드포인트는_열려_있지_않음() throws Exception {
    for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/info" }) {
      assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(404);
    }
  }
}
