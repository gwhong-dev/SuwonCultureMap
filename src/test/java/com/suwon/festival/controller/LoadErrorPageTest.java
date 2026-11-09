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

// 행사 불러오기 실패 안내: PC 에도 보이고, 받기 실패와 그리기 실패를 나눠 안내 (#91 검증 지적)
@SpringBootTest
@ActiveProfiles("test")
class LoadErrorPageTest {

  @Autowired private WebApplicationContext context;

  private String fetch(String path) throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }

  @Test
  void PC_기본_스타일에서_실패_안내를_숨기지_않음() throws Exception {
    String css = fetch("/css/index.css");

    assertThat(css).contains(".skeleton-card {\n  display: none;\n}");
    assertThat(css).doesNotContain(".skeleton-card,\n.load-error {\n  display: none;");
    assertThat(css).contains(".load-error {\n  display: flex;");
  }

  @Test
  void 받기_실패와_그리기_실패를_나눠_안내() throws Exception {
    String js = fetch("/js/index.js");

    // 받기(fetch · JSON) 실패만 "network", 그 뒤 화면 그리기 오류는 "render"
    assertThat(js).contains(".then(showEvents, function (err) {\n      failLoad(\"network\", err);");
    assertThat(js).contains(".catch(function (err) {\n      failLoad(\"render\", err);");
    assertThat(js).contains("network: \"행사를 불러오지 못했어요.");
    assertThat(js).contains("render: \"행사를 화면에 표시하지 못했어요.");
  }
}
