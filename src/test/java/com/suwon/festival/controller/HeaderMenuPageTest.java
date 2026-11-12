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

// P4 상단 바 · ☰ 메뉴 (docs/design/2026-11-13-improvement-drafts.md)
// 비로그인 = [로그인] 만 / 로그인 = 이름 + [마이페이지] / 서비스 안내 · 개인정보 처리방침 = ☰ 메뉴 줄
@SpringBootTest
@ActiveProfiles("test")
class HeaderMenuPageTest {

  @Autowired private WebApplicationContext context;

  private String fetch(String path) throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }

  private static String between(String text, String start, String end) {
    int from = text.indexOf(start);
    assertThat(from).as(start).isNotNegative();
    return text.substring(from, text.indexOf(end, from));
  }

  @Test
  void 로그인_상태_글자는_없고_로그인했을_때만_이름() throws Exception {
    String html = fetch("/index.html");
    String js = fetch("/js/index.js");
    assertThat(html + js).doesNotContain("로그인 안 됨", "로그인됨", "로그인 확인 중");
    assertThat(html).contains("<span id=\"authStatus\" hidden></span>");
    assertThat(js).contains("status.hidden = !isLoggedIn;", "status.textContent = memberLabel(data);");
  }

  @Test
  void 회원_이름_글자는_함수_하나에서만_정함() throws Exception {
    String js = fetch("/js/index.js");
    // P5(#239): 닉네임이 있으면 닉네임, 없으면 회원 번호
    assertThat(js).contains("function memberLabel(data) {\n  return data.nickname || \"회원 #\" + data.memberId;\n}");
    assertThat(js.split("회원 #", -1)).hasSize(2); // 이름 글자는 memberLabel 한 곳뿐
  }

  @Test
  void 서비스_안내와_처리방침은_메뉴_줄에만_있음() throws Exception {
    String html = fetch("/index.html");
    String menu = between(html, "<div id=\"menuLinks\"", "</div>");
    assertThat(menu).contains("href=\"about.html\">서비스 안내</a>", "href=\"privacy.html\">개인정보 처리방침</a>");
    assertThat(menu).contains("<button id=\"logoutBtn\" type=\"button\" class=\"menu-link\" hidden>로그아웃</button>");
    // 상단 바 바깥 단추로는 없음
    assertThat(html.split("href=\"about.html\"", -1)).hasSize(2);
    assertThat(html.split("href=\"privacy.html\"", -1)).hasSize(2);
    assertThat(html).contains("<button id=\"mobileMenuBtn\" type=\"button\" aria-label=\"메뉴\" aria-expanded=\"false\">☰</button>");
  }

  @Test
  void 메뉴_단추는_PC에서도_보이고_메뉴_줄은_40px() throws Exception {
    String css = fetch("/css/index.css");
    String mobileOnly = between(css, "/* ===== 모바일 전용 요소: PC에서는 숨김 ===== */", "}");
    assertThat(mobileOnly).doesNotContain("#mobileMenuBtn");
    assertThat(between(css, "\n.menu-link {", "}")).contains("height: 40px;");
    assertThat(css).contains("#topbar.menu-open .menu-links {\n  display: flex;\n}");
  }
}
