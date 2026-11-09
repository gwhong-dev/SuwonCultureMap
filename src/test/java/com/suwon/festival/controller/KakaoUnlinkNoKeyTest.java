package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 어드민 키가 비어 있으면 "KakaoAK " 만 보낸 요청도 통과하면 안 됨 → 항상 401
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "kakao.admin-key=")
class KakaoUnlinkNoKeyTest {

  @Autowired private WebApplicationContext context;

  @Test
  void 어드민_키가_없으면_빈_키_헤더도_401() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    for (String auth : new String[] { "KakaoAK ", "KakaoAK" }) {
      int status = mockMvc.perform(post("/api/auth/kakao/unlink-callback").header("Authorization", auth)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("user_id", "1001"))
          .andReturn().getResponse().getStatus();
      assertThat(status).isEqualTo(401);
    }
  }
}
