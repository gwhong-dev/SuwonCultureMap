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
    assertThat(js).contains("onFavScreen() ? \"찜 \" : \"행사 \""); // P3 시안 "행사 32개"
  }

  @Test
  void 홈으로_나가면_찜_화면_흔적을_지우고_둘러보기는_찜_칩을_풂() throws Exception {
    String js = script();

    // 홈(뒤로 가기 · 홈 탭)으로 나가면 다시 그려서 탭 줄 · 칩 흐림 · "찜 N개"가 남지 않게 (PR #160 검증 ❌)
    assertThat(js).contains("    favTabOpen = false;\n    filterMarkers();");
    // 빈 안내 "행사 둘러보기": '♥ 찜' 칩이 켜져 있으면 전체로 (PR #160 검증 ❌)
    assertThat(js).contains("if (select.value === \"찜\") select.value = \"all\";");
  }
}
