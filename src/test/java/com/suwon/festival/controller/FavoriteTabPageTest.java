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

// 하단 탭 "찜"(모바일) = 따로 된 화면. 분류 드롭다운 값을 바꾸지 않음 (#85 검증 지적)
@SpringBootTest
@ActiveProfiles("test")
class FavoriteTabPageTest {

  @Autowired private WebApplicationContext context;

  private String script() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc.perform(get("/js/index.js")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }

  @Test
  void 찜_탭은_분류_값을_바꾸지_않음() throws Exception {
    String js = script();

    // 탭 선택은 favTabOpen 만 바꿈 → 공연 칩 → 찜 탭 → 지도 탭이면 공연 그대로
    assertThat(js).contains("favTabOpen = tab === \"fav\";");
    assertThat(js).doesNotContain("select.value = \"all\"; // 찜 탭에서 나오면 전체로");
    // 하단 탭 선택 표시도 분류 값이 아니라 favTabOpen 기준 → '♥ 찜' 칩은 지도 탭의 필터
    assertThat(js).contains("if (favTabOpen) return \"fav\";");
    assertThat(js).doesNotContain("if (document.getElementById(\"categorySelect\").value === \"찜\") return \"fav\";");
  }

  @Test
  void 찜_탭은_지도_쪽_조건과_상관없이_찜_전부() throws Exception {
    String js = script();

    assertThat(js).contains("if (favScreen) {");
    assertThat(js).contains("if (favoriteEventIds.has(item.event.id)) visibleItems.push(item);");
    // 찜 · 최근 본 모두 칩을 흐리게 (조건이 안 걸림)
    assertThat(js).contains("document.body.classList.toggle(\"fav-screen\", visible);");
    assertThat(js).contains("onFavScreen() ? \"찜 \" : \"검색 결과 \"");
  }
}
