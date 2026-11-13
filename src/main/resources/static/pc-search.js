// PC 검색 · 분류 규칙 (11/13 확정 시안 R4 A · R5 A4 — docs/design/2026-11-13-pc-pending.md "② 검색 · 분류")
//  - 분류 펼침 목록 순서 · 개수, 검색 결과로 지도를 옮기는 방법, 펼침 목록 키보드
//  - 화면(DOM · 카카오 지도)은 index.js 가 그리고, 여기는 계산만 (node 시험)
// 시험: node --test src/test/js/ (CI 에서 실행)

// 펼침 목록 순서: 전체 분류 · 행사 · 공연 · 전시 · 축제 · 기타 / 구분선 / 찜한 행사만
// 값은 #categorySelect 값 그대로 ("all" · 분류 이름 · "찜")
var PC_CATEGORY_MENU = ["all", "행사", "공연", "전시", "축제", "기타", "찜"];

// 분류마다 행사 수 = 분류만 빼고 다른 조건(날짜 탭 · 무료 · 검색 · 구)을 통과한 행사 중 그 분류
//  events = 그 행사들, isFavorite(id) = 찜 여부 → { all, 행사, …, 찜 }
//  메뉴에 없는 분류(예: 스포츠)는 "전체 분류" 에만 셈
//  favEvents = '찜' 을 골랐을 때 걸리는 조건으로 고른 행사 (진행 탭은 '찜' 에선 안 걸림 — calendar-strip.js statusTabApplies)
//    → 찜 개수 = 그중 찜한 행사 수 (고른 뒤 목록 수와 같게). 없으면 events 로 셈
function pcCategoryCounts(events, isFavorite, favEvents) {
  var counts = {};
  PC_CATEGORY_MENU.forEach(function (value) {
    counts[value] = 0;
  });
  events.forEach(function (event) {
    counts.all += 1;
    if (event.category !== "all" && event.category !== "찜" && counts.hasOwnProperty(event.category)) {
      counts[event.category] += 1;
    }
  });
  (favEvents || events).forEach(function (event) {
    if (isFavorite(event.id)) counts["찜"] += 1;
  });
  return counts;
}

// 펼침 목록에 보일 줄: 메뉴 순서에서 "전체 분류" · "찜한 행사만" 은 늘, 분류는 #categorySelect 에 있는 것만
//  available = #categorySelect 옵션 값들 (행사 데이터에 있는 분류 + all · 찜)
function pcCategoryRows(available) {
  return PC_CATEGORY_MENU.filter(function (value) {
    return value === "all" || value === "찜" || available.indexOf(value) >= 0;
  });
}

// 검색 결과로 지도 옮기기 (치는 동안 300ms 쉬면): points = 결과 행사 [{ latitude, longitude }]
//  0개 → { kind: "none" } 지도 그대로
//  1곳 → { kind: "center", lat, lng } 그 행사로 (같은 장소 여러 행사도 한 곳)
//  여러 곳 → { kind: "bounds", points: [{ lat, lng }] } 결과 핀이 모두 보이게 (index.js 가 setBounds 여백 60px)
function pcSearchMapMove(points) {
  var seen = {};
  var unique = [];
  points.forEach(function (p) {
    if (p.latitude == null || p.longitude == null) return;
    var key = p.latitude + "," + p.longitude;
    if (seen[key]) return;
    seen[key] = true;
    unique.push({ lat: p.latitude, lng: p.longitude });
  });
  if (unique.length === 0) return { kind: "none" };
  if (unique.length === 1) return { kind: "center", lat: unique[0].lat, lng: unique[0].lng };
  return { kind: "bounds", points: unique };
}

// 검색 결과 지도 이동을 기다리는 시간 (치는 동안 이만큼 쉬면 이동)
var PC_SEARCH_MAP_DELAY = 300;

// 분류 펼침 목록 키보드 (WAI-ARIA listbox): 칩에서 / 목록에서 누른 키 → 할 일
//  open = 목록이 열려 있나, index = 지금 고른 줄(활성 줄), count = 줄 수
//  → { action: "open" | "move" | "pick" | "close" | "none", index }
function pcCategoryKey(key, open, index, count) {
  if (!open) {
    if (key === "Enter" || key === " " || key === "ArrowDown") return { action: "open", index: index };
    if (key === "ArrowUp") return { action: "open", index: index };
    return { action: "none", index: index };
  }
  if (key === "ArrowDown") return { action: "move", index: Math.min(count - 1, index + 1) };
  if (key === "ArrowUp") return { action: "move", index: Math.max(0, index - 1) };
  if (key === "Home") return { action: "move", index: 0 };
  if (key === "End") return { action: "move", index: count - 1 };
  if (key === "Enter" || key === " ") return { action: "pick", index: index };
  if (key === "Escape" || key === "Tab") return { action: "close", index: index };
  return { action: "none", index: index };
}

if (typeof module !== "undefined") {
  module.exports = {
    PC_CATEGORY_MENU: PC_CATEGORY_MENU,
    PC_SEARCH_MAP_DELAY: PC_SEARCH_MAP_DELAY,
    pcCategoryCounts: pcCategoryCounts,
    pcCategoryRows: pcCategoryRows,
    pcSearchMapMove: pcSearchMapMove,
    pcCategoryKey: pcCategoryKey,
  };
}
