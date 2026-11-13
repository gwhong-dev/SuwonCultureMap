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

    assertThat(html).contains("<select id=\"dateFilter\"", "<option value=\"today\" ", "<option value=\"weekend\" ");
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
  void 찜_정렬_규칙_파일을_화면_스크립트보다_먼저_불러옴() throws Exception {
    // 찜 [날짜순 | 이름순] 정렬 · 시작일 비교(sortFavorites · compareStartDate)가 fav-sort.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/fav-sort.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/fav-sort.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
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
    // 지금 진행 중 · 곧 종료 · 곧 시작 · 달별 고르기(nowOpenCards · endingSoon · monthGroups …)가 home-picks.js 에 있음 → index.js 보다 앞
    String html = page();
    int rules = html.indexOf("<script src=\"/home-picks.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(rules).isGreaterThan(html.indexOf("<script src=\"/schedule-format.js\"></script>")); // 상태 계산을 씀
    assertThat(html).contains("id=\"homeView\"", "id=\"homeSoonList\"", "data-tab=\"home\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/home-picks.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 홈은_지금_진행_중_곧_종료_곧_시작_달별_행사_3칸_순서이고_이번_주_줄은_없음() throws Exception {
    // 11/11 사용자 확정 시안: ① 지금 진행 중 N (가로 카드, 전체 →) ② 곧 종료 N | 곧 시작 N (7일 안) ③ 달별 행사
    String html = page();
    int now = html.indexOf("<section id=\"homeNow\"");
    int soon = html.indexOf("id=\"homeSoonList\"");
    int months = html.indexOf("<section id=\"homeMonths\"");
    assertThat(now).isPositive().isLessThan(soon);
    assertThat(months).isGreaterThan(soon).isLessThan(html.indexOf("<section id=\"homeNearby\""));
    assertThat(html).contains("id=\"homeNowCount\"", "id=\"homeNowAll\"", "id=\"homeNowTrack\"");
    assertThat(html).contains("id=\"homeEndingCount\"", "id=\"homeStartingCount\"", "7일 안</span>");
    assertThat(html.substring(months, html.indexOf(">", months))).contains(" hidden"); // 고를 달이 있을 때만 보임
    assertThat(html).contains("id=\"homeMonthChips\"", "id=\"homeMonthTrack\"");
    assertThat(html).contains("<option value=\"week\" data-i18n=\"filter.week\">7일 안</option>"); // "7일 안" = 지도 날짜 칩과 같은 기간 (글은 화면 언어 i18n.js)
    // 옛 홈: 이번 주 줄 · 포스터 캐러셀 · 날짜 칩 · 이달의 행사 없음
    assertThat(html).doesNotContain("이번 주 수원 행사", "id=\"homeCarousel\"", "id=\"homeDateChips\"", "id=\"homeMonth\"");

    String js = script();
    assertThat(js).contains("nowOpenCards(events)", "nowOpenEvents(events).length", "monthGroups(homeEvents())");
    assertThat(js).contains("group.label + \" · \" + group.events.length"); // 달 칩 "12월 · 14"
    assertThat(js).doesNotContain("carouselPicks", "thisWeekEvents", "weekRange");
    // "전체 →" = 목록 탭 + 오늘 진행 (다른 필터는 풀어서 목록 수 = N)
    assertThat(js).contains("document.getElementById(\"dateFilter\").value = \"today\";\n  freeOnly = false;");
    assertThat(js).doesNotContain("showPastCheckbox", "showPast:"); // 11/13: 숨은 '지난 행사 보기' 체크박스 삭제
  }

  @Test
  void 지도_위_날짜_칩은_없고_날짜는_목록_위_날짜_줄_달력_PC_탭에서_고름() throws Exception {
    // 사용자 확정 시안(11/11 CalClosed · CalOpen · PcCard): 지도 칩 줄의 진행 중 · 이번 주말 · 7일 안 칩 삭제
    String js = script();
    assertThat(js).doesNotContain("label: \"📅 진행 중\"", "label: \"📅 이번 주말\"", "chip-pc-only", "오늘 진행");
    // 날짜 필터가 켜져 있으면 지도 칩 줄에 해제 칩만 (모바일 전용)
    assertThat(js).contains("chip.id = \"dateChip\";", "chip.className = \"chip chip-mobile-only\";");
    // 고른 날짜는 같은 #dateFilter 값 → 같은 그날 진행 판정 (filterDays · matchRunDays)
    assertThat(js).contains("function setDateFilter(value)", "matchesStatusTab(item.event, statusTab)");
    // PC 기본 = 전체(진행 중 + 진행 예정, 11/12 사용자), 켜진 탭을 다시 누르면 전체로
    assertThat(js).contains("var pcStatusTab = null;", "currentStatusTab() === tab.dataset.status ? null : tab.dataset.status");

    String html = page();
    assertThat(html).contains("id=\"datePanel\"", "id=\"dateStrip\"", "id=\"calMonth\"", "id=\"calGrid\"");
    assertThat(html).contains("data-status=\"open\"", "data-status=\"upcoming\"", "id=\"pcCalBtn\"");
    assertThat(html.indexOf("id=\"datePanel\"")).isLessThan(html.indexOf("<div id=\"list\"></div>")); // 목록 위
    int rules = html.indexOf("<script src=\"/calendar-strip.js\"></script>");
    assertThat(rules).isGreaterThan(html.indexOf("<script src=\"/schedule-format.js\"></script>"));
    assertThat(rules).isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("<option value=\"today\" data-i18n=\"filter.today\">진행 중</option>", "<option value=\"week\" data-i18n=\"filter.week\">7일 안</option>"); // 홈 날짜 칩 값

    // 모바일 목록 화면도 PC 와 같은 탭 줄 (P3) → 모바일 = 2주 날짜 줄 숨김, PC = 날짜 줄 · 해제 칩 숨김
    String css = fetch("/css/index.css");
    int mobile = css.indexOf("@media (max-width: 1024px)");
    assertThat(css.indexOf("  .strip-head,", mobile)).isGreaterThan(mobile);
    assertThat(css).contains("#categoryChips .chip-mobile-only,");
  }

  @Test
  void 달력_지난_날짜_하루_이동_연월_고르기가_연결됨() throws Exception {
    // 11/13 P2: '지난 행사' 칩 삭제('무료' 칩은 유지), 지난 날을 고르면 그날 열린 행사(종료 포함)
    String js = script();
    assertThat(js).doesNotContain("🕘 지난 행사", "\"__past\"", "button.disabled = isPastDay(date);");
    assertThat(js).contains("freeChip.textContent = I18n.t(\"chip.free\");"); // "💸 무료" (i18n/ko.js)
    assertThat(js).contains("hidesEndedEvents(dateValue);", "matchesOthers && matchesPast && matchesDate");
    assertThat(js).contains("addDays(day, i === 0 ? -1 : 1)", "monthStepLabel(calShownMonth, nav[1])");

    String html = page();
    assertThat(html).contains("id=\"dayNav\"", "id=\"dayPrevBtn\"", "id=\"dayNextBtn\"", "id=\"pastDayNote\"");
    assertThat(html).contains("id=\"calPicker\"", "id=\"calPickerMonths\"", "aria-controls=\"calPicker\"");
    // 빠른 고르기는 "2026년 11월 ▾" 줄 바로 아래 붙어 달력을 덮음
    assertThat(html.indexOf("id=\"calPicker\"")).isLessThan(html.indexOf("id=\"calGrid\""));

    // 하루 이동은 늘 한 줄, 좁으면 글자 빼고 ◂ ▸ 만
    String css = fetch("/css/index.css");
    assertThat(css).contains("container: daynav / inline-size;", "@container daynav (max-width: 300px)", "white-space: nowrap;");
  }

  @Test
  void 모바일_지도_탭_카드_줄_목록_보기_홈_끝_넘김_상세_넘기기가_있음() throws Exception {
    // P3 확정 시안 (docs/design/2026-11-13-improvement-drafts.md): 지도 탭 카드 기본 표시 · 목록 보기 = 지도를 다 덮는 화면
    String html = page();
    assertThat(html).contains("id=\"previewPlaceBand\"", "id=\"listMapBtn\"", "id=\"detailPager\"", "id=\"detailPrev\"", "id=\"detailNext\"");
    assertThat(html.indexOf("id=\"previewPlaceBand\"")).isLessThan(html.indexOf("id=\"previewStripWrap\"")); // 띠는 카드 줄 위
    // 떠 있는 [지도 보기] 는 시트 밖 (시트의 transform 안에 있으면 fixed 가 화면 기준이 아님, #240 검증 ❌)
    assertThat(html.indexOf("id=\"listMapBtn\"")).isGreaterThan(html.indexOf("</aside>"));

    String js = script();
    // 모바일도 지도 탭이면 카드 줄 기본 (홈 · 목록 · 찜 · 검색이 덮으면 안 띄움), 위치 글 = "밀어서 다음 행사"
    assertThat(js).contains("stripDefaultShown(mobileQuery.matches, lastSortedItems.length, mobileMapShown())");
    assertThat(js).contains("stripLabelText(!!previewOrigin, index, cards.length, mobileQuery.matches)");
    assertThat(js).doesNotContain("mapClickClosesStrip", "\"half\"", "preview-stack");
    // 홈 끝 넘김 → 지도 탭 + 진행 중, 카드 줄에서 연 상세는 좌우로 앞뒤 행사
    assertThat(js).contains("homeEndSwipe(start.atEnd,", "showNowEvents(\"map\")", "showNowEvents(\"list\")");
    assertThat(js).contains("stripDetailPager(item.event.id)", "swipeStep(t.clientX - start.x, t.clientY - start.y)");

    String css = fetch("/css/index.css");
    int mobile = css.indexOf("@media (max-width: 1024px)");
    assertThat(css.indexOf("writing-mode: vertical-rl;", mobile)).isGreaterThan(mobile); // 세로 글자 "더 밀면 전체"
    assertThat(css.indexOf("body.sheet-full:not(.fav-screen) #listMapBtn {", mobile)).isGreaterThan(mobile);
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
  void 모바일_상단_바는_터치를_지도로_통과시키지_않음() throws Exception {
    // 아이폰 사파리: 통과(pointer-events: none) 부모 안의 가로 스크롤 칩 줄 터치가 지도로 새어
    // 칩을 누른 채 끌면 지도가 같이 움직였음 (11/11) → 상단 바가 터치를 직접 받음
    String css = fetch("/css/index.css");
    int topbar = css.indexOf("  #topbar {\n    position: absolute;");
    assertThat(topbar).isPositive();
    assertThat(css.substring(topbar, css.indexOf("}", topbar))).doesNotContain("pointer-events: none");
    assertThat(css).doesNotContain("#topbar > * {\n    pointer-events: auto;");

    // CSS 만으로는 아이폰에서 안 고쳐짐 → 상단 바 · 칩 줄에서 시작한 터치 동안 지도 끌기 끔, 손 떼면 켬
    String js = script();
    assertThat(js).contains("guardMapDragFromTopbar();\n  loadEvents();");
    assertThat(js).contains("map.setDraggable(false);", "map.setDraggable(true);", "\"touchcancel\", release");
    assertThat(js).contains("{ capture: true, passive: true }"); // 카카오보다 먼저 · 스크롤은 막지 않음
  }

  @Test
  void PC_도_지도_위에_칩_줄이_있고_상단_바_분류_날짜_칸은_숨김() throws Exception {
    // 사용자: "웹버전 지도 상단에 카테고리/날짜 토글 배치, 모바일처럼" → 칩 줄을 PC 에서도 지도 왼쪽 위에, 값은 숨긴 칸에 그대로
    String css = fetch("/css/index.css");
    int pcChips = css.indexOf("/* PC: 칩 줄을 지도 왼쪽 위에 띄움");
    assertThat(pcChips).isPositive();
    String pc = css.substring(pcChips, css.indexOf("/* ====", pcChips));
    assertThat(pc).contains("@media (min-width: 1025px)", "#categorySelect,", "#dateFilter {", "#categoryChips {");
    assertThat(css).doesNotContain(".past-toggle");
    // PC 숨김 목록(모바일 전용 요소)에서 칩 줄이 빠짐
    String hidden = css.substring(css.indexOf("/* ===== 모바일 전용 요소: PC에서는 숨김"), css.indexOf("display: none;", css.indexOf("/* ===== 모바일 전용 요소")));
    assertThat(hidden).doesNotContain("#categoryChips");
    // PC 로 넘어갈 때 구 · 무료 필터를 풀던 코드 없음 (PC 칩 줄에서 끌 수 있음)
    assertThat(script()).doesNotContain("PC 엔 칩 줄이 없어");
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
    // (날짜 해제 칩도 같은 자리에서 다시 맞춤 — 11/11 캘린더)
    assertThat(js).contains("updateDistrictChip();\n  updateDateChip();\n}", "chip.classList.add(\"active\");");
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
    assertThat(js).contains("stripArrowState(", "stripStepTarget(", "wheelStep(", "stripKeyStep(", "stripLabelText(");
    // 모바일 지도 탭은 카드 줄을 기본으로 띄움 (P3) — 마커를 누르지 않아도. PC 는 11/13 Q4 부터 상세 칸
    assertThat(js).contains("function showDefaultPreview(", "showDefaultPreview();");
    // 예전 PC 말풍선 InfoWindow 는 없앰
    assertThat(js).doesNotContain("kakao.maps.InfoWindow");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/strip-nav.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void 화면_언어_글_묶음과_i18n_js를_head_에서_먼저_읽고_언어_고르기는_화면_스크립트_앞() throws Exception {
    // 11/13 확정 시안 Q3: 글 묶음 4개 → i18n.js → theme.js (첫 그리기 전에 <html lang>), lang-picker.js 는 index.js 앞
    String html = page();
    int ko = html.indexOf("<script src=\"/i18n/ko.js\"></script>");
    int i18n = html.indexOf("<script src=\"/i18n.js\"></script>");
    assertThat(ko).isPositive().isLessThan(i18n);
    assertThat(i18n).isLessThan(html.indexOf("<script src=\"/theme.js\"></script>"));
    assertThat(html.indexOf("<script src=\"/lang-picker.js\"></script>")).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"langBtn\"", "id=\"langPop\"", "id=\"homeLangBtn\"", "id=\"menuLangBtn\"");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    for (String path : new String[] {"/i18n.js", "/lang-picker.js", "/i18n/ko.js", "/i18n/en.js", "/i18n/ja.js", "/i18n/zh.js"}) {
      assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(200);
    }
  }

  @Test
  void 데이터_갱신_시각은_마이_페이지_맨_아래에만_있음() throws Exception {
    // 11/11 사용자 결정: 목록 결과 줄(모바일 · PC)에는 안 넣고, 마이 페이지를 열었을 때만
    String index = page();
    assertThat(index).doesNotContain("data-sync-status", "sync-status.js");
    assertThat(fetch("/css/index.css")).doesNotContain(".sync-status");

    // 마이 페이지 맨 아래 정보 칸: 1줄 갱신 시각(값 오기 전 · 실패면 hidden) · 2줄 출처(고정)
    String mypage = fetch("/mypage.html");
    int info = mypage.indexOf("<div class=\"data-info\">");
    int sync = mypage.indexOf("<p class=\"data-info-line\" data-sync-status hidden></p>");
    int source = mypage.indexOf("<p class=\"data-info-line\" data-i18n=\"mypage.dataSource\">출처 · 수원문화재단 공공데이터 · 수원문화DB, 공식 인스타그램 계정</p>");
    assertThat(info).isGreaterThan(mypage.indexOf("← 지도로 돌아가기"));
    assertThat(sync).isGreaterThan(info).isLessThan(source);
    assertThat(mypage.indexOf("<script src=\"/sync-status.js\"></script>")).isPositive();
    assertThat(fetch("/css/mypage.css")).contains(".data-info-line", "font-size: 12px;");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/sync-status.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(fetch("/sync-status.js")).contains("/api/sync-status", "[data-sync-status]", "syncStatusText(", "행사 정보 마지막 갱신 · ");
  }

  @Test
  void PC_는_지도_상세_목록_3칸이고_지도_아래_카드_줄은_없음() throws Exception {
    // 11/13 확정 시안 Q4 PcNavD1 (docs/design/2026-11-13-todo-additions.md "Q4"): 지도 1fr | 상세 440px(늘 열림) | 목록 340px
    String html = page();
    int map = html.indexOf("<div id=\"map\"></div>");
    int pane = html.indexOf("<section id=\"pcDetail\"");
    int sidebar = html.indexOf("<aside id=\"sidebar\">");
    assertThat(map).isPositive().isLessThan(pane);
    assertThat(pane).isLessThan(sidebar);
    // 포스터 아래 ‹ 1 / 30 › 줄 (aria-keyshortcuts · 키 표시 줄 연결) · 키 표시 줄 · 읽어 주는 곳 한 곳 (위치 점은 없앰 — R1 B)
    assertThat(html).contains(
        "id=\"pcdPrev\"", "aria-keyshortcuts=\"ArrowLeft\"", "id=\"pcdNext\"", "aria-keyshortcuts=\"ArrowRight\"",
        "id=\"pcdFav\"", "aria-keyshortcuts=\"Space\"", "aria-describedby=\"kbdHint\"",
        "id=\"pcdPos\"", "id=\"kbdHint\"", "id=\"pcdLive\" class=\"sr-only\" aria-live=\"polite\"");
    assertThat(html).doesNotContain("id=\"pcdDots\"");
    // ‹ › 와 위치 글은 포스터 칸 밖, 포스터 바로 아래 줄 (‹ · 1 / 30 · ›), 툴팁은 짧은 글 "이전 [←]" · "다음 [→]" (R3 A)
    int posterBox = html.indexOf("id=\"pcdPosterBox\"");
    int pager = html.indexOf("<div class=\"pcd-pager\">");
    assertThat(posterBox).isPositive().isLessThan(pager);
    assertThat(html.indexOf("id=\"pcdPrev\"")).isGreaterThan(pager).isLessThan(html.indexOf("id=\"pcdPos\""));
    assertThat(html.indexOf("id=\"pcdPos\"")).isLessThan(html.indexOf("id=\"pcdNext\""));
    assertThat(html).contains("data-i18n=\"pcd.tipPrev\">이전</span> <kbd>←</kbd>", "data-i18n=\"pcd.tipNext\">다음</span> <kbd>→</kbd>");
    // PC 카드 줄 안내는 없어짐
    assertThat(html).doesNotContain("id=\"previewHint\"");

    String css = fetch("/css/index.css").replace("\r\n", "\n");
    assertThat(css).contains("--sidebar-width: 340px;", "--pc-detail-width: 440px;");
    int media = css.indexOf("@media (min-width: 1025px) {\n  #app {");
    assertThat(media).isPositive();
    String pc = block(css, css.indexOf("{", media));
    assertThat(rule(pc, "#app {")).contains("grid-template-columns: minmax(0, 1fr) var(--pc-detail-width) var(--sidebar-width);");
    // 포스터 칸 4:3 고정 (440 − 20×2 = 400×300, R1 B) — 남는 높이 채우기는 없앰, 잘리지 않게 contain
    assertThat(rule(pc, ".pcd-main {")).doesNotContain("min-height: 100%;");
    assertThat(rule(pc, ".pcd-poster-box {")).contains("flex: none;", "aspect-ratio: 4 / 3;").doesNotContain("min-height: 240px;");
    assertThat(rule(pc, ".pcd-pager {")).contains("height: 40px;", "justify-content: space-between;");
    assertThat(rule(pc, ".pcd-poster .pcd-img {")).contains("object-fit: contain;");
    // ‹ › 40px 원 모양 고정 (R2): 처음 · 끝은 화살표만 --line-strong, opacity 로 흐리게 하지 않음 · 포커스 2겹 고리
    assertThat(rule(pc, ".pcd-arrow {")).contains("width: 40px;", "border-radius: 50%;", "border: 1px solid var(--line);", "background: var(--bg);", "0 1px 4px rgba(0, 0, 0, 0.18)");
    assertThat(rule(pc, ".pcd-arrow[aria-disabled=\"true\"] {")).contains("color: var(--line-strong);").doesNotContain("opacity");
    assertThat(rule(pc, ".pcd-arrow:not([aria-disabled=\"true\"]):hover {")).contains("background: var(--surface);", "border-color: var(--line-strong);");
    // 툴팁 (R3 A): 밝은 바탕 · 단추 가로 가운데 · 마우스 · Esc 로 닫힘
    assertThat(rule(pc, ".pcd-tip {")).contains("background: var(--bg);", "left: 50%;", "transform: translateX(-50%);", "bottom: calc(100% + 6px);");
    assertThat(rule(pc, ".pcd-arrow:focus-visible {")).contains("0 0 0 2px var(--bg), 0 0 0 4px var(--text)");
    assertThat(pc).contains("#pcDetail.tips-off .pcd-tip {");
    // 지도 아래 카드 줄은 PC 에서 숨김 (모바일만)
    int pcStrip = css.indexOf("  /* 지도 하단 카드 줄은 모바일만");
    assertThat(pcStrip).isPositive();
    assertThat(rule(css.substring(pcStrip), "#previewArea {")).contains("display: none;");
    // 모바일은 상세 칸 숨김 (공통 규칙)
    assertThat(css).contains("#pcDetail,\n.row-pick,");

    String js = script();
    // 키 범위는 pc-card.js (node 시험), 길찾기 작은 창(routePopEvent)이 열려 있으면 넘기지 않음 (Q4 문서 — 전 코드 빈틈)
    assertThat(js).contains("pcKeyZone(", "pcKeyAction(", "pcStepId(", "pcPagerState(");
    assertThat(js).doesNotContain("pcPagerDots(", "pcdDots");
    assertThat(js).contains("function pcPopupOpen()", "!!routePopEvent ||");
    // PC 는 상세 창(덮개) 대신 상세 칸, 찜 토스트는 찜할 때만 1.6초
    assertThat(js).contains("pcSelect(event.id, { announce: true, pan: true, outside: true });", "var PC_FAV_TOAST_MS = 1600;");
    assertThat(js).doesNotContain("pcCardKeyAction(", "pcToastBottom(");
    // PC 첫 화면은 축척 250m(레벨 5) + 상세 칸 첫 행사가 중심 (11/13 확정 B1 · B2 — 전엔 네 곳 범위로 100m)
    assertThat(js).contains("level: mobileQuery.matches ? 6 : PC_MAP_LEVEL,", "function pcCenterFirst()", "pcFavoritesReady = true;");
    assertThat(js).doesNotContain("fitPcFirstView", "PC_FIRST_VIEW");
  }

  @Test
  void PC_지도_확대_단추와_먼_행사_이동은_확정_시안대로() throws Exception {
    // 11/13 확정 시안 R6 · R7 (docs/design/2026-11-13-pc-pending.md "③ 지도 · 키")
    String html = page();
    assertThat(html).contains(
        "<div id=\"mapZoom\" role=\"group\" aria-label=\"지도 확대 축소\" data-i18n-attr=\"aria-label:map.zoomGroup\">",
        "id=\"mapZoomIn\"", "aria-label=\"지도 확대\"", "id=\"mapZoomOut\"", "aria-label=\"지도 축소\"");
    String css = fetch("/css/index.css").replace("\r\n", "\n");
    int media = css.indexOf("@media (min-width: 1025px) {\n  #app {");
    assertThat(media).isPositive();
    String pc = block(css, css.indexOf("{", media));
    assertThat(rule(pc, "#mapZoom {")).contains("margin: 0 16px 24px 0;", "border-radius: 8px;", "box-shadow: var(--shadow-float);");
    assertThat(rule(pc, ".map-zoom-btn {")).contains("width: 40px;", "height: 40px;");
    assertThat(rule(pc, ".map-zoom-btn[aria-disabled=\"true\"] {")).contains("color: var(--line-strong);");
    // 모바일은 숨김 (공통 규칙)
    assertThat(rule(css, "#mapZoom {")).contains("display: none;");

    String js = script();
    assertThat(js).contains("function setupPcZoom()", "pcZoomState(", "map.setLevel(level, { animate: true });");
    // 먼 행사: 축소 → panTo → 원래 레벨, 움직임 줄이기면 setCenter (판정은 pc-card.js pcMoveKind — node 시험)
    assertThat(js).contains("function pcMoveMap(position, reduce)", "pcMoveKind(", "if (pan) pcMoveMap(");
  }

  @Test
  void PC_길찾기는_버튼_위_작은_창이고_모바일_하단_카드는_그대로() throws Exception {
    // 11/13 확정 시안 P1 의 PC 길찾기 작은 창은 상세 칸 [길찾기] 에서 그대로, 모바일 하단 카드 · 띠는 P3 그대로
    String html = page();
    int rules = html.indexOf("<script src=\"/pc-card.js\"></script>");
    assertThat(rules).isPositive().isLessThan(html.indexOf("<script src=\"/js/index.js\"></script>"));
    assertThat(html).contains("id=\"previewPlaceBand\"", "id=\"routePop\"", "카카오맵에서 길찾기", "네이버 지도에서 길찾기", "id=\"routePopCopy\"");

    String js = script();
    assertThat(js).contains("if (mobileQuery.matches) openRouteSheet(item.event);", "toggleRoutePop(item.event, this);");
    assertThat(js).contains("dialogFocus.opened(\"routePop\"", "dialogFocus.closed(\"routePop\")");
    // 같은 장소 다음 행사로 넘기면 마커가 튀어오름 (움직임 줄이기 설정이면 생략)
    assertThat(js).contains("shouldHopMarker(");
    // 모바일 지금 카드는 보조기기에도 알림 (#215 검증 ⑦)
    assertThat(js).contains("card.setAttribute(\"aria-current\", \"true\")", "card.removeAttribute(\"aria-current\")");

    String css = fetch("/css/index.css").replace("\r\n", "\n");
    assertThat(css).contains(".route-pop:not([hidden]) {");
    // 공통(모바일) 카드 줄 규칙은 그대로 띠 전체가 받음
    assertThat(rule(css, "\n#previewArea.show {")).contains("pointer-events: auto;");
    int reduce = css.indexOf("@media (prefers-reduced-motion: reduce)");
    assertThat(block(css, css.indexOf("{", reduce))).contains(".marker-hop-icon,", ".marker-hop-ring,");

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    assertThat(mockMvc.perform(get("/pc-card.js")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  // open = 여는 { 의 위치 → 짝이 맞는 } 까지 (안쪽 내용)
  private static String block(String css, int open) {
    int depth = 0;
    for (int i = open; i < css.length(); i++) {
      if (css.charAt(i) == '{') depth++;
      else if (css.charAt(i) == '}' && --depth == 0) return css.substring(open + 1, i);
    }
    throw new IllegalStateException("닫는 } 없음");
  }

  // selector 로 시작하는 규칙 하나의 내용 ({ … })
  private static String rule(String css, String selector) {
    int at = css.indexOf(selector);
    assertThat(at).as(selector).isNotNegative();
    return block(css, css.indexOf("{", at));
  }
}
