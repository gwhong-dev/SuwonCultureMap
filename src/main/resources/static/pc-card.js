// PC 지도 하단 카드 규칙 (11/13 확정 시안 P1 — docs/design/2026-11-13-improvement-drafts.md)
//  - 배지 · 기간 줄 · 장소 줄 글자, 마커 튀어오름 판단, 길찾기 작은 창 자리
//  - 11/13 Q4 D안부터 PC 지도 아래 카드 줄은 없어지고 상세 칸(index.js renderPcDetail)이 이 글자 규칙을 씀
//  - 모바일 하단 카드는 그대로 (P3 담당) — 여기 함수는 PC 카드만 씀
// 시험: node --test src/test/js/ (CI 에서 실행)

// 화면 언어 (i18n.js, node 시험 = 한국어)
var pcI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;
var PC_STATUS_LABELS = { open: pcI18n.t("status.pcOpen"), upcoming: pcI18n.t("status.pcUpcoming"), ended: pcI18n.t("status.ended") };

// 배지 2개: 상태(진행 중 · 오픈 예정 · 종료) + D-day(종료까지 D-37 · D-2). 날짜 없는 행사는 빈 배열
// statusOf · ddayOf = schedule-format.js eventStatus · ddayText (시험에서 바꿔 넣을 수 있게 인자로 받음)
function pcCardBadges(event, statusOf, ddayOf) {
  var status = statusOf(event);
  if (!status) return [];
  var badges = [{ kind: "status-badge " + status, text: PC_STATUS_LABELS[status] }];
  var dday = ddayOf(event);
  if (dday) badges.push({ kind: "dday-badge", text: dday });
  return badges;
}

function pcDotDate(isoDate, withYear) {
  var p = isoDate.split("-");
  return (withYear ? p[0] + "." : "") + p[1] + "." + p[2];
}

// 기간 줄: "08.01 – 12.19 · 주말". 올해가 아닌 날짜가 끼면 연도까지. summary = 요일 요약 · 총 N회 (없으면 null)
//  - "토 · 일" 은 "주말" 로 줄임 (카드 한 줄 폭)
function pcCardPeriod(event, thisYear, summary) {
  if (!event.startDate) return pcI18n.t("sched.noPeriod");
  var end = event.endDate || event.startDate;
  var withYear = event.startDate.slice(0, 4) !== thisYear || end.slice(0, 4) !== thisYear;
  var range =
    end === event.startDate
      ? pcDotDate(event.startDate, withYear)
      : pcDotDate(event.startDate, withYear) + " – " + pcDotDate(end, withYear);
  if (!summary) return range;
  var weekend = pcI18n.weekday(6) + " · " + pcI18n.weekday(0);
  return range + " · " + (summary === weekend ? pcI18n.t("sched.weekend") : summary);
}

// 장소 줄: "정조테마공연장 · 팔달구" (둘 다 없으면 null → 줄 숨김)
function pcCardPlace(place, district) {
  var parts = [];
  if (place) parts.push(place);
  if (district && (!place || place.indexOf(district) === -1)) parts.push(district);
  return parts.length ? parts.join(" · ") : null;
}

// 같은 장소 다음 카드로 넘길 때만 마커가 튀어오름 (지도가 움직이지 않아 넘긴 걸 알리려고)
//  - 다른 장소로 넘기면 지도가 그 마커로 움직이니 생략, 움직임 줄이기 설정이면 생략
function shouldHopMarker(prevMarker, nextMarker, prevId, nextId, reduceMotion) {
  return !reduceMotion && !!prevMarker && prevMarker === nextMarker && prevId !== nextId;
}

// ===== PC 3분할 D안 (11/13 확정 시안 Q4 PcNavD1 — docs/design/2026-11-13-todo-additions.md "Q4") =====
// 지도 | 상세 칸(늘 열림) | 목록. 상세 칸 포스터 양옆 ‹ › · 키보드 ← → 로 오른쪽 목록 순서대로 넘김

// Space 를 가로채지 않는 단추 역할 (단추 · 링크 · 고르기 줄 — 그 요소 기본 동작)
var PC_CONTROL_ROLES = ["button", "link", "menuitem", "option", "checkbox", "radio", "switch", "slider", "combobox", "listbox"];

// 포커스 자리 → 키 범위 구역 (Q4 "키가 먹는 범위" 표의 줄)
// el = { tag, editable(contenteditable), role, row(오른쪽 목록 줄), pane(상세 칸 자체) }
function pcKeyZone(el) {
  if (el.editable || el.tag === "INPUT" || el.tag === "TEXTAREA" || el.tag === "SELECT") return "input";
  if (el.role === "tab") return "tab";
  if (el.row) return "row";
  if (el.pane) return "pane";
  if (el.tag === "BUTTON" || el.tag === "A" || el.tag === "SUMMARY" || PC_CONTROL_ROLES.indexOf(el.role) >= 0) return "control";
  return "other"; // 지도 · 목록 스크롤 영역 · 빈 곳 · 상세 칸 본문
}

// 키 하나 → 할 일. k = { key, zone(pcKeyZone), popup(길찾기 작은 창 · 공유 창 · ☰ 메뉴 · 언어 목록 · 달력이 열림),
//   mods(Alt · Ctrl · Meta · Shift 함께), handled(defaultPrevented), composing(한글 조합 중) }
// 돌려줌: "prev" · "next" 넘기기 / "fav" 찜 / "focusPane" 상세 칸으로 포커스 / null = 가로채지 않음(기본 동작)
//  - 입력칸 · 조합 중 · select · contenteditable, 탭 줄(role=tab) → 기본
//  - 작은 창 · 메뉴 · 달력이 열림, 조합키, 이미 처리됨 → 넘기지 않음 (그 창 · 브라우저 기본)
//  - 목록 줄: ← → ↑ ↓ 넘기기 · Space 찜(그 줄 = 상세 행사) · Enter 상세 칸으로
//  - 상세 칸 자체 · 상세 칸 본문 · 지도 · 목록 스크롤 영역 · 빈 곳: ← → 넘기기 · Space 상세 행사 찜
//    (11/13 확정 B3 — docs/design/2026-11-13-pc-pending.md "③ 지도 · 키", 전엔 Space 가 스크롤 · 안 먹음)
//  - 단추 · 링크: ← → 만 (Space 는 단추 기본 동작)
function pcKeyAction(k) {
  if (k.handled || k.mods || k.composing || k.popup) return null;
  if (k.zone === "input" || k.zone === "tab") return null;
  var key = k.key;
  var space = key === " " || key === "Spacebar";
  var side = key === "ArrowLeft" ? "prev" : key === "ArrowRight" ? "next" : null;
  if (side) return side;
  if (k.zone === "row") {
    if (key === "ArrowUp") return "prev";
    if (key === "ArrowDown") return "next";
    if (space) return "fav";
    if (key === "Enter") return "focusPane";
    return null;
  }
  if (space && (k.zone === "pane" || k.zone === "other")) return "fav";
  return null;
}

// ===== 지도 이동 (11/13 확정 R7 · B2 — docs/design/2026-11-13-pc-pending.md "③ 지도 · 키") =====
var PC_MAP_LEVEL = 5; // PC 첫 화면 축척 250m (B2). 모바일은 그대로
var PC_MOVE_OUT = 2; // 먼 행사로 갈 때 잠깐 넓힐 레벨 (250m → 1km 정도)
var PC_MOVE_MS = { out: 300, back: 300 }; // 축소 · 확대 움직임 시간 (가운데 panTo 는 카카오 기본 속도)
var PC_ZOOM_MIN = 1; // 카카오 지도 레벨 범위 (1 = 가장 크게, 14 = 가장 작게)
var PC_ZOOM_MAX = 14;

// 고른 행사 자리 → 지도 움직임. view = 지금 화면 { south, west, north, east }, point = { lat, lng }
//  - "inside": 화면 안 → 옮기지 않음 / "near": 화면 밖이지만 한 칸 안쪽(바로 옆) → panTo 만
//  - "far": 화면 한 칸(가로 · 세로 크기)보다 더 떨어짐 → 축소 → 이동 → 확대
function pcMoveKind(view, point) {
  var height = view.north - view.south;
  var width = view.east - view.west;
  if (point.lat >= view.south && point.lat <= view.north && point.lng >= view.west && point.lng <= view.east) return "inside";
  var far =
    point.lat < view.south - height ||
    point.lat > view.north + height ||
    point.lng < view.west - width ||
    point.lng > view.east + width;
  return far ? "far" : "near";
}

// + / − 단추 상태: 가장 크게(레벨 min) · 가장 작게(레벨 max) 면 그 단추를 끔
function pcZoomState(level, min, max) {
  return { zoomIn: level > min, zoomOut: level < max };
}

// 넘길 행사 id: ids = 오른쪽 목록 순서, 처음 · 끝에서는 멈춤(null). 지금 행사가 목록에 없으면 첫 행사
function pcStepId(ids, currentId, delta) {
  if (!ids.length) return null;
  var index = ids.indexOf(currentId);
  if (index < 0) return ids[0];
  var target = index + delta;
  if (target < 0 || target >= ids.length) return null;
  return ids[target];
}

// 위치 글 "3 / 6" (행사가 없으면 빈 글)
function pcPosText(index, count) {
  return count > 0 ? Math.max(index, 0) + 1 + " / " + count : "";
}

// 포스터 아래 ‹ 3 / 30 › 줄 (확정 시안 R1 B · R2): 처음 · 끝이면 그쪽 단추 멈춤(화살표만 흐리게), 가운데 위치 글
// 지금 행사가 목록에 없으면(필터로 빠짐) 위치 글은 비우고, 넘기기는 첫 행사로 (pcStepId)
function pcPagerState(ids, currentId) {
  var index = ids.indexOf(currentId);
  return {
    canPrev: pcStepId(ids, currentId, -1) !== null,
    canNext: pcStepId(ids, currentId, 1) !== null,
    text: index < 0 ? "" : pcPosText(index, ids.length),
  };
}

// 길찾기 작은 창 자리: [길찾기] 버튼 위 가운데, 화면 밖으로 나가지 않게. 위 자리가 모자라면 버튼 아래
// button = { left, top, width, bottom }(화면 좌표), pop = { width, height }, view = { width, height }
var ROUTE_POP_GAP = 8;
function routePopPosition(button, pop, view) {
  var margin = 8;
  var left = button.left + button.width / 2 - pop.width / 2;
  left = Math.max(margin, Math.min(left, view.width - pop.width - margin));
  var top = button.top - pop.height - ROUTE_POP_GAP;
  if (top < margin) top = Math.min(button.bottom + ROUTE_POP_GAP, view.height - pop.height - margin);
  return { left: Math.round(left), top: Math.round(Math.max(margin, top)) };
}

if (typeof module !== "undefined") {
  module.exports = {
    PC_STATUS_LABELS: PC_STATUS_LABELS,
    pcCardBadges: pcCardBadges,
    pcCardPeriod: pcCardPeriod,
    pcCardPlace: pcCardPlace,
    shouldHopMarker: shouldHopMarker,
    pcKeyZone: pcKeyZone,
    pcKeyAction: pcKeyAction,
    PC_MAP_LEVEL: PC_MAP_LEVEL,
    PC_MOVE_OUT: PC_MOVE_OUT,
    PC_MOVE_MS: PC_MOVE_MS,
    PC_ZOOM_MIN: PC_ZOOM_MIN,
    PC_ZOOM_MAX: PC_ZOOM_MAX,
    pcMoveKind: pcMoveKind,
    pcZoomState: pcZoomState,
    pcStepId: pcStepId,
    pcPosText: pcPosText,
    pcPagerState: pcPagerState,
    routePopPosition: routePopPosition,
  };
}
