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

// 메인 화면의 진행 날짜 표시가 연결되어 있는지 확인 (표시 규칙 자체는 src/test/js 에서 시험)
@SpringBootTest
@ActiveProfiles("test")
class ScheduleDisplayPageTest {

  @Autowired private WebApplicationContext context;

  private String page() throws Exception {
    return fetch("/index.html");
  }

  // 화면 스크립트는 CSP(script-src) 때문에 js/index.js 로 분리됨
  private String script() throws Exception {
    return fetch("/js/index.js");
  }

  private String fetch(String path) throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    // Windows 체크아웃은 줄바꿈이 \r\n 이라 여러 줄 문자열 비교가 실패함 → \n 으로 맞춤
    return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
  }

  @Test
  void 목록_미리보기_상세가_같은_표시_함수를_씀() throws Exception {
    String js = script();

    // 목록 · 미리보기는 날짜 필터의 "기간 중 · 일정 확인" 을 붙이는 cardDateText 를 거침
    assertThat(js).contains("var text = formatEventDates(item.event)");
    assertThat(js).contains("var dateText = cardDateText(item)");
    assertThat(js).contains("escapeHtml(cardDateText(item))");
    assertThat(js).contains("document.getElementById(\"modalDate\").textContent = cardDateText({");
  }

  @Test
  void 캘린더_버튼은_아이폰만_ics_나머지는_구글_캘린더() throws Exception {
    String html = page();
    String js = script();

    assertThat(html).contains("id=\"modalCalBtn\"", "id=\"modalIcsAltLink\"", "id=\"modalCalDate\"");
    assertThat(html).doesNotContain("id=\"modalGoogleBtn\""); // 구글 버튼을 따로 두지 않음
    assertThat(js).contains("if (isAppleMobile) {\n      calBtn.href = icsUrl;");
    assertThat(js).contains("calBtn.href = googleUrl;");
  }

  @Test
  void 날짜_필터가_목록_필터에_연결됨() throws Exception {
    String html = page();
    String js = script();

    assertThat(html).contains("<select id=\"dateFilter\"", "<option value=\"today\">", "<option value=\"weekend\">");
    assertThat(js).contains("filterDays(document.getElementById(\"dateFilter\").value)");
    assertThat(js).contains("matchRunDays(item.event, runDays)");
  }

  @Test
  void 상세에_일정_칸이_있고_표시_규칙_파일을_불러옴() throws Exception {
    assertThat(page()).contains("id=\"modalSchedule\"", "<script src=\"/schedule-format.js\"></script>");
    assertThat(page()).contains("<script src=\"/js/index.js\"></script>"); // 화면 스크립트 본체

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/schedule-format.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 상세_표시_규칙_파일을_화면_스크립트보다_먼저_불러옴() throws Exception {
    // 인스타 행사(info · 사진 없음) 상세 규칙 · extractPlaceName 이 event-detail.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/event-detail.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"modalImage\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/event-detail.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 찜_안내_문구_파일을_화면_스크립트보다_먼저_불러옴() throws Exception {
    // 찜 토스트 · 찜 탭 빈 상태 문구(favoriteToast · favoriteEmptyState)가 favorite-ui.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/favorite-ui.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"toast\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/favorite-ui.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 모바일_홈_화면과_홈_탭이_있고_홈_규칙_파일을_화면_스크립트보다_먼저_불러옴() throws Exception {
    // 이번 주 · 곧 종료 · 곧 시작 고르기(weekRange · endingSoon …)가 home-picks.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/home-picks.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(rules).isGreaterThan(html.indexOf("<script src=\"/schedule-format.js\"></script>")); // 상태 계산을 씀
    assertThat(html).contains("id=\"homeView\"", "id=\"homeSoonList\"", "data-tab=\"home\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/home-picks.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 홈_포스터_캐러셀이_곧_종료_탭보다_위에_있고_처음엔_숨김() throws Exception {
    // carouselPicks 로 고른 행사가 있을 때만 index.js 가 보이게 함 (없으면 빈 칸이 남지 않게)
    String html = page();
    int carousel = html.indexOf("<section id=\"homeCarousel\"");
    assertThat(carousel).isPositive().isLessThan(html.indexOf("id=\"homeSoonList\""));
    assertThat(html.substring(carousel, html.indexOf(">", carousel))).contains(" hidden");
    assertThat(html).contains("id=\"homeCarouselTrack\"", "id=\"homeCarouselFill\"", "id=\"homeCarouselCount\"");
  }

  @Test
  void 홈_날짜_칩_3개가_캐러셀과_곧_종료_사이에_있고_PC_선택상자에_7일_안() throws Exception {
    // 칩 값은 #dateFilter 값과 같아야 지도 탭으로 넘길 때 그대로 씀
    String html = page();
    int chips = html.indexOf("id=\"homeDateChips\"");
    assertThat(chips).isGreaterThan(html.indexOf("id=\"homeCarousel\"")).isLessThan(html.indexOf("id=\"homeSoonList\""));
    assertThat(html.substring(chips, html.indexOf("</nav>", chips)))
        .contains("data-date=\"today\"", "data-date=\"weekend\"", "data-date=\"week\"", "+7일");
    assertThat(html).contains("<option value=\"week\">7일 안</option>");
  }

  @Test
  void 홈_이달의_행사가_날짜_칩과_곧_종료_사이에_있고_처음엔_숨김() throws Exception {
    // monthEvents 로 고른 행사가 있을 때만 index.js 가 보이게 함 (제목 "11월의 수원 행사"도 index.js 가 채움)
    String html = page();
    int month = html.indexOf("<section id=\"homeMonth\"");
    assertThat(month).isGreaterThan(html.indexOf("id=\"homeDateChips\"")).isLessThan(html.indexOf("id=\"homeSoonList\""));
    assertThat(html.substring(month, html.indexOf(">", month))).contains(" hidden");
    assertThat(html).contains("id=\"homeMonthTitle\"", "id=\"homeMonthTrack\"");
  }
}
