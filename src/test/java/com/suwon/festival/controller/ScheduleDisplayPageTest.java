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
  void 구_필터_상태가_목록_필터에_연결됨() throws Exception {
    // 버튼은 아직 없음 (H5 에서 setDistrictFilter 호출). 처음엔 전체(null)
    String js = script();

    assertThat(js).contains("var districtFilter = null;", "function setDistrictFilter(district)");
    assertThat(js).contains("matchesDistrict(item.event, districtFilter)");
    assertThat(js).doesNotContain("extractDistrict"); // 구 표시는 서버 값 우선인 eventDistrict 하나로
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
  void 찜_탭_최근_본_규칙_파일을_화면_스크립트보다_먼저_불러오고_탭_줄이_있음() throws Exception {
    // 최근 본 번호 읽기 · 더하기 · 고르기(parseRecentIds · addRecentId · pickRecent)가 recent-views.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/recent-views.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"favTabs\"", "data-view=\"fav\"", "data-view=\"recent\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/recent-views.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 찜_알림_규칙_파일을_상태_계산_뒤_화면_스크립트보다_먼저_불러오고_찜_탭_점이_있음() throws Exception {
    // 내일 시작 · 3일 안 종료 고르기(favoriteAlerts)가 favorite-alerts.js 에 있음 → schedule-format.js 뒤, index.js 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/favorite-alerts.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(rules).isGreaterThan(html.indexOf("<script src=\"/schedule-format.js\"></script>"));
    assertThat(html).contains("id=\"favTabDot\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/favorite-alerts.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
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
  void 모바일_지도_칩_줄의_분류는_드롭다운_하나() throws Exception {
    // 분류 칩 7개(전체 · 행사 · 공연 …)를 늘어놓으면 오른쪽으로 한참 넘겨야 함 → 칩 하나 + 투명 <select>
    String js = script();
    assertThat(js).contains("categoryChip.className = \"chip chip-select\"", "categoryPicker.id = \"categoryChipSelect\"");
    assertThat(js).contains("select.value = categoryPicker.value;"); // 고르면 PC 드롭다운 값으로 필터
    assertThat(js).contains("categoryPicker.value = selected;"); // 다른 곳에서 바꾼 값도 칩에 맞춤
    assertThat(js).doesNotContain("select.value = option.value;"); // 분류마다 칩을 만들던 옛 방식 없음

    String css = fetch("/css/index.css");
    assertThat(css).contains(".chip-select select {", "opacity: 0;");
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

  @Test
  void 홈_내_주변이_곧_종료_아래에_있고_처음엔_숨김_지도에서_보기는_구_필터를_켬() throws Exception {
    // 구 칩 · 작은 지도 · "○○구 행사 N개" 는 index.js 가 채움 (고르는 규칙은 home-picks.js districtEvents)
    String html = page();
    int nearby = html.indexOf("<section id=\"homeNearby\"");
    assertThat(nearby).isGreaterThan(html.indexOf("id=\"homeSoonList\""));
    assertThat(html.substring(nearby, html.indexOf(">", nearby))).contains(" hidden");
    assertThat(html).contains("id=\"homeDistrictChips\"", "id=\"homeNearbyMap\"", "id=\"homeNearbyGo\"");

    String js = script();
    assertThat(js).contains("districtEvents(homeEvents(), homeDistrict)", "showDistrictOnMap(homeDistrict, nearbyPicks)", "districtFilter = district;");
    assertThat(js).contains("setDistrictFilter(null)"); // 지도 칩 줄 "📍 ○○구 ✕" 로 해제
    // 칩 줄 선택 표시 루프(updateChipActive)가 다시 돌아도 구 칩은 켜진 모양 유지 (검증 ❌ 반영)
    assertThat(js).contains("updateDistrictChip();\n}", "chip.classList.add(\"active\");");
  }

  @Test
  void 모바일_검색_화면이_있고_처음엔_숨김_지도_필터도_같은_검색_규칙() throws Exception {
    // 홈 검색 버튼 · 지도 탭 검색창 → #searchView. 고르기 · 강조 규칙은 home-picks.js (searchEvents · searchHighlight)
    String html = page();
    int search = html.indexOf("<section id=\"searchView\"");
    assertThat(search).isPositive();
    assertThat(html.substring(search, html.indexOf(">", search))).contains(" hidden");
    assertThat(html).contains("id=\"searchViewInput\"", "id=\"searchSuggest\"", "id=\"searchBackBtn\"");

    String js = script();
    assertThat(js).contains("addEventListener(\"click\", openSearch)", "searchEvents(events, keyword, undefined, typing)");
    // 입력 중에만 치는 중 규칙 ("숭" → 수원), 엔터 결과는 그대로 (11/9)
    assertThat(js).contains("var typing = !results && searchTyping(events, keyword);");
    assertThat(js).contains("matchesSearch(item.event, keyword)"); // 지도 · 목록도 제목 + 장소, 띄어쓰기 무시
    assertThat(js).doesNotContain("item.event.title.includes(keyword)");
    // 엔터 → 결과(07): 홈과 같은 행사 행 + ♡, 찜이 바뀌면 filterMarkers 끝에서 ♡ 표시를 맞춤
    assertThat(js).contains("searchMode = \"results\"", "eventRowHtml(event) +", "updateSearchFavs();\n  updateFavAlertDot();\n}");
  }

  @Test
  void 뒤로_가기_기록_파일을_화면_스크립트보다_먼저_불러오고_화면마다_칸을_쌓음() throws Exception {
    // 홈 → 지도 · 목록 · 찜, 검색 화면, 상세, 길찾기 · 공유 창 → 브라우저 뒤로 가기로 닫힘 (nav-history.js)
    String html = page();
    int rules = html.indexOf("<script src=\"/nav-history.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));

    String js = script();
    assertThat(js).contains("window.addEventListener(\"popstate\", nav.onPopState)");
    assertThat(js).contains("nav.open(\"tab\")", "nav.open(\"search\")", "nav.open(\"detail\")", "nav.open(\"route\")", "nav.open(\"share\")");
    assertThat(js).contains("nav.forget(\"share\")"); // 카카오톡으로 넘어가는 중엔 go(-1) 을 미룸
    assertThat(js).doesNotContain("history.replaceState("); // 주소 기록은 nav 가 칸과 함께 맞춤

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/nav-history.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 상세_길찾기_공유_창은_열리면_첫_버튼으로_닫히면_원래_자리로_포커스() throws Exception {
    // 보조기기용 (#89 검증 지적) — dialog-focus.js
    String html = page();
    int rules = html.indexOf("<script src=\"/dialog-focus.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("class=\"modal-card\" role=\"dialog\" aria-modal=\"true\"");

    String js = script();
    assertThat(js).contains("dialogFocus.opened(\"detail\"", "dialogFocus.opened(\"route\"", "dialogFocus.opened(\"share\"");
    assertThat(js).contains("dialogFocus.closed(\"detail\")", "dialogFocus.closed(\"route\")", "dialogFocus.closed(\"share\")");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/dialog-focus.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 지도_하단_카드_줄에_마우스용_좌우_화살표와_휠_이동이_있음() throws Exception {
    // 마우스 PC 는 카드를 잡고 밀 수 없어서 좌우 화살표 · 휠로 한 장씩 (strip-nav.js) → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/strip-nav.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"previewPrev\"", "id=\"previewNext\"", "aria-label=\"이전 카드\"", "aria-label=\"다음 카드\"");

    String js = script();
    assertThat(js).contains("stripArrowState(", "stripStepTarget(", "wheelStep(");
    // PC 도 모바일과 같은 하단 카드 (예전 PC 말풍선 InfoWindow 는 없앰)
    assertThat(js).doesNotContain("kakao.maps.InfoWindow");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/strip-nav.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }
}
