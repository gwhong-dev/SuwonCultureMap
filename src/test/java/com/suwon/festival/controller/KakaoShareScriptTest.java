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

// 카카오톡 공유 버튼: SDK v2 는 Kakao.init() 뒤에야 Kakao.Share 가 생김
// → init 전에 Share 를 검사하면 버튼이 항상 "불러오지 못했어요" 로 끝남 (11/3 운영에서 발견)
@SpringBootTest
@ActiveProfiles("test")
class KakaoShareScriptTest {

  @Autowired private WebApplicationContext context;

  @Test
  void 공유_버튼은_init_뒤에_Share를_확인함() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    String js = mockMvc.perform(get("/js/index.js")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

    int handler = js.indexOf("getElementById(\"shareKakaoBtn\").addEventListener");
    assertThat(handler).as("공유 버튼 처리").isNotNegative();
    String body = js.substring(handler);

    int init = body.indexOf("Kakao.init(KAKAO_JS_KEY)");
    int shareCheck = body.indexOf("!Kakao.Share");
    assertThat(init).as("Kakao.init 호출").isNotNegative();
    assertThat(shareCheck).as("Kakao.Share 확인").isNotNegative();
    assertThat(init).as("init 이 Share 확인보다 먼저").isLessThan(shareCheck);
  }
}
