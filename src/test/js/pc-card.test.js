// PC 카드 규칙 시험 (11/13 확정 시안 P1 · Q4 D안) — 배지 · 기간 줄 · 장소 줄 · 마커 튀어오름 · 키 범위 · 넘기기 · 위치 점 · 길찾기 창 자리
const test = require("node:test");
const assert = require("node:assert");
const {
  pcCardBadges,
  pcCardPeriod,
  pcCardPlace,
  shouldHopMarker,
  pcKeyZone,
  pcKeyAction,
  pcStepId,
  PC_MAP_LEVEL,
  PC_MOVE_OUT,
  PC_MOVE_MS,
  PC_ZOOM_MIN,
  PC_ZOOM_MAX,
  pcMoveKind,
  pcZoomState,
  PC_DOTS_MAX,
  pcPagerDots,
  pcPosText,
  routePopPosition,
} = require("../../main/resources/static/pc-card.js");

test("배지: 진행 중 + 종료까지 D-N, 오픈 예정 + D-N (시안 글자), 날짜 없으면 없음", () => {
  const open = pcCardBadges({}, () => "open", () => "종료까지 D-37");
  assert.deepStrictEqual(open, [
    { kind: "status-badge open", text: "진행 중" },
    { kind: "dday-badge", text: "종료까지 D-37" },
  ]);
  const soon = pcCardBadges({}, () => "upcoming", () => "D-2");
  assert.deepStrictEqual(soon.map((b) => b.text), ["오픈 예정", "D-2"]);
  assert.deepStrictEqual(pcCardBadges({}, () => "ended", () => null), [{ kind: "status-badge ended", text: "종료" }]);
  assert.deepStrictEqual(pcCardBadges({}, () => null, () => null), []);
});

test("기간 줄: 올해면 'MM.DD – MM.DD · 요약', 토 · 일은 '주말'", () => {
  const event = { startDate: "2026-08-01", endDate: "2026-12-19" };
  assert.strictEqual(pcCardPeriod(event, "2026", "토 · 일"), "08.01 – 12.19 · 주말");
  assert.strictEqual(pcCardPeriod(event, "2026", "총 3회"), "08.01 – 12.19 · 총 3회");
  assert.strictEqual(pcCardPeriod(event, "2026", null), "08.01 – 12.19");
});

test("기간 줄: 하루 행사는 날짜 하나, 올해가 아닌 날짜가 끼면 연도까지, 날짜 없으면 안내", () => {
  assert.strictEqual(pcCardPeriod({ startDate: "2026-11-21", endDate: "2026-11-21" }, "2026", null), "11.21");
  assert.strictEqual(pcCardPeriod({ startDate: "2026-11-21" }, "2026", null), "11.21");
  assert.strictEqual(
    pcCardPeriod({ startDate: "2026-12-01", endDate: "2027-02-28" }, "2026", null),
    "2026.12.01 – 2027.02.28",
  );
  assert.strictEqual(pcCardPeriod({}, "2026", null), "기간 정보 없음");
});

test("장소 줄: '장소 · 구', 장소 이름에 구가 들어 있으면 한 번만, 둘 다 없으면 null", () => {
  assert.strictEqual(pcCardPlace("정조테마공연장", "팔달구"), "정조테마공연장 · 팔달구");
  assert.strictEqual(pcCardPlace("팔달구청", "팔달구"), "팔달구청");
  assert.strictEqual(pcCardPlace("정조테마공연장", null), "정조테마공연장");
  assert.strictEqual(pcCardPlace(null, "팔달구"), "팔달구");
  assert.strictEqual(pcCardPlace(null, null), null);
});

test("마커 튀어오름: 같은 장소 마커의 다른 카드로 넘길 때만, 움직임 줄이기면 생략", () => {
  const a = {};
  const b = {};
  assert.strictEqual(shouldHopMarker(a, a, 1, 2, false), true);
  assert.strictEqual(shouldHopMarker(a, b, 1, 2, false), false); // 다른 장소 = 지도가 움직임
  assert.strictEqual(shouldHopMarker(a, a, 1, 1, false), false);
  assert.strictEqual(shouldHopMarker(a, a, 1, 2, true), false);
  assert.strictEqual(shouldHopMarker(null, null, null, 2, false), false); // 처음 고름
});

// ===== 11/13 Q4 D안 — docs/design/2026-11-13-todo-additions.md "키가 먹는 범위" 표 그대로 =====
const ALL_KEYS = ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", " ", "Enter"];
const act = (zone, key, extra) => pcKeyAction(Object.assign({ key, zone }, extra));

test("키 범위 구역: 입력칸 · select · contenteditable / 탭 / 목록 줄 / 상세 칸 / 단추 · 링크 / 그 밖", () => {
  assert.strictEqual(pcKeyZone({ tag: "INPUT" }), "input");
  assert.strictEqual(pcKeyZone({ tag: "TEXTAREA" }), "input");
  assert.strictEqual(pcKeyZone({ tag: "SELECT" }), "input");
  assert.strictEqual(pcKeyZone({ tag: "DIV", editable: true }), "input");
  assert.strictEqual(pcKeyZone({ tag: "BUTTON", role: "tab" }), "tab");
  assert.strictEqual(pcKeyZone({ tag: "DIV", row: true }), "row");
  assert.strictEqual(pcKeyZone({ tag: "SECTION", pane: true }), "pane");
  assert.strictEqual(pcKeyZone({ tag: "BUTTON" }), "control");
  assert.strictEqual(pcKeyZone({ tag: "A" }), "control");
  assert.strictEqual(pcKeyZone({ tag: "DIV", role: "button" }), "control");
  assert.strictEqual(pcKeyZone({ tag: "BODY" }), "other");
  assert.strictEqual(pcKeyZone({ tag: "DIV" }), "other"); // 지도 · 목록 스크롤 영역 · 상세 칸 본문
  // 고르기 줄 · 접기 단추도 단추 (Space = 그 요소 기본 동작, 11/13 B3)
  for (const role of ["menuitem", "option", "checkbox", "radio", "switch", "slider", "combobox", "listbox"]) {
    assert.strictEqual(pcKeyZone({ tag: "DIV", role }), "control", role);
  }
  assert.strictEqual(pcKeyZone({ tag: "SUMMARY" }), "control");
});

test("키 범위: 입력칸 · 한글 조합 중 · 탭 줄은 모든 키가 기본", () => {
  for (const key of ALL_KEYS) {
    assert.strictEqual(act("input", key), null, "input " + key);
    assert.strictEqual(act("tab", key), null, "tab " + key);
    assert.strictEqual(act("other", key, { composing: true }), null, "조합 " + key);
    assert.strictEqual(act("row", key, { composing: true }), null, "조합 줄 " + key);
  }
});

test("키 범위: 길찾기 작은 창 · 공유 창 · 메뉴 · 언어 목록 · 달력이 열리면 넘기기 · 찜 막음", () => {
  for (const zone of ["row", "pane", "control", "other"]) {
    for (const key of ALL_KEYS) assert.strictEqual(act(zone, key, { popup: true }), null, zone + " " + key);
  }
});

test("키 범위: 조합키(Alt · Ctrl · Meta · Shift) · 이미 처리된 키는 막음", () => {
  for (const zone of ["row", "pane", "control", "other"]) {
    for (const key of ALL_KEYS) {
      assert.strictEqual(act(zone, key, { mods: true }), null, zone + " mods " + key);
      assert.strictEqual(act(zone, key, { handled: true }), null, zone + " handled " + key);
    }
  }
});

test("키 범위: 목록 줄 = ← → ↑ ↓ 넘기기 · Space 찜 · Enter 상세 칸으로", () => {
  assert.strictEqual(act("row", "ArrowLeft"), "prev");
  assert.strictEqual(act("row", "ArrowRight"), "next");
  assert.strictEqual(act("row", "ArrowUp"), "prev");
  assert.strictEqual(act("row", "ArrowDown"), "next");
  assert.strictEqual(act("row", " "), "fav");
  assert.strictEqual(act("row", "Spacebar"), "fav"); // 옛 브라우저
  assert.strictEqual(act("row", "Enter"), "focusPane");
  assert.strictEqual(act("row", "a"), null); // 글자 키 단축키 없음 (WCAG 2.1.4)
});

test("키 범위: 상세 칸 자체 = ← → 넘기기 · Space 찜, ↑ ↓ · Enter 기본", () => {
  assert.strictEqual(act("pane", "ArrowLeft"), "prev");
  assert.strictEqual(act("pane", "ArrowRight"), "next");
  assert.strictEqual(act("pane", " "), "fav");
  assert.strictEqual(act("pane", "ArrowUp"), null);
  assert.strictEqual(act("pane", "ArrowDown"), null);
  assert.strictEqual(act("pane", "Enter"), null);
});

test("키 범위: 단추 · 링크(‹ › · 찜 · 길찾기 · 원문 · + / −) = ← → 만, Space · Enter 는 단추 기본 동작", () => {
  assert.strictEqual(act("control", "ArrowLeft"), "prev");
  assert.strictEqual(act("control", "ArrowRight"), "next");
  assert.strictEqual(act("control", " "), null);
  assert.strictEqual(act("control", "Spacebar"), null);
  assert.strictEqual(act("control", "Enter"), null);
  assert.strictEqual(act("control", "ArrowUp"), null);
  assert.strictEqual(act("control", "ArrowDown"), null);
  assert.strictEqual(act("control", "?"), null); // ? 단축키 창 없음
});

// 11/13 확정 B3 — docs/design/2026-11-13-pc-pending.md "③ 지도 · 키" Space 표
test("키 범위 B3: 지도 · 목록 스크롤 영역 · 빈 곳 · 상세 칸 본문에서도 Space = 상세 행사 찜, ← → 넘기기", () => {
  // 지도(누르고 있거나 포커스) · 목록 칸 스크롤 영역(DIV) · 빈 곳(BODY) · 상세 칸 본문(DIV) = 모두 "other"
  for (const el of [{ tag: "BODY" }, { tag: "DIV" }]) {
    const zone = pcKeyZone(el);
    assert.strictEqual(zone, "other");
    assert.strictEqual(act(zone, " "), "fav", el.tag + " Space");
    assert.strictEqual(act(zone, "Spacebar"), "fav", el.tag + " 옛 Space");
    assert.strictEqual(act(zone, "ArrowLeft"), "prev");
    assert.strictEqual(act(zone, "ArrowRight"), "next");
    assert.strictEqual(act(zone, "Enter"), null);
    assert.strictEqual(act(zone, "ArrowUp"), null); // ↑ ↓ 는 스크롤 그대로
    assert.strictEqual(act(zone, "ArrowDown"), null);
    assert.strictEqual(act(zone, "?"), null);
  }
  assert.strictEqual(act("pane", " "), "fav"); // 상세 칸 자체
  assert.strictEqual(act("row", " "), "fav"); // 목록 줄 = 그 줄(= 상세 행사)
});

test("키 범위 B3: 입력칸 · 한글 조합 · select · contenteditable · 단추 · 링크 · 열린 창이면 Space 는 그대로", () => {
  for (const el of [{ tag: "INPUT" }, { tag: "TEXTAREA" }, { tag: "SELECT" }, { tag: "DIV", editable: true }]) {
    assert.strictEqual(act(pcKeyZone(el), " "), null, el.tag);
  }
  assert.strictEqual(act("other", " ", { composing: true }), null);
  for (const el of [{ tag: "BUTTON" }, { tag: "A" }, { tag: "DIV", role: "button" }, { tag: "DIV", role: "option" }]) {
    assert.strictEqual(act(pcKeyZone(el), " "), null, el.tag + " " + (el.role || ""));
  }
  // 길찾기 작은 창 · 공유 창 · ☰ 메뉴 · 언어 · 분류 펼침 목록 · 달력이 열림 → 막음
  for (const zone of ["other", "pane", "row"]) assert.strictEqual(act(zone, " ", { popup: true }), null, zone);
  assert.strictEqual(act("other", " ", { mods: true }), null); // Shift+Space 등
  assert.strictEqual(act("other", " ", { handled: true }), null);
});

// ===== 11/13 확정 R7 · B2 · R6 — 지도 이동 · 확대 단추 =====
const VIEW = { south: 37.27, west: 127.0, north: 37.29, east: 127.03 }; // 세로 0.02 · 가로 0.03

test("지도 이동 판정: 화면 안 → inside (옮기지 않음)", () => {
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.28, lng: 127.015 }), "inside");
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.27, lng: 127.0 }), "inside"); // 가장자리
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.29, lng: 127.03 }), "inside");
});

test("지도 이동 판정: 화면 밖이지만 한 칸 안쪽(바로 옆) → near (panTo 만)", () => {
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.295, lng: 127.015 }), "near"); // 위로 조금
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.28, lng: 127.059 }), "near"); // 오른쪽으로 화면 한 칸 거의
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.251, lng: 126.971 }), "near"); // 왼쪽 아래 대각선, 한 칸 안쪽
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.309, lng: 127.059 }), "near"); // 오른쪽 위, 한 칸 안쪽
});

test("지도 이동 판정: 화면 한 칸 넘게 떨어짐 → far (축소 → 이동 → 확대)", () => {
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.315, lng: 127.015 }), "far"); // 위로 한 칸 넘게
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.245, lng: 127.015 }), "far"); // 아래
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.28, lng: 127.07 }), "far"); // 오른쪽
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.28, lng: 126.96 }), "far"); // 왼쪽
  assert.strictEqual(pcMoveKind(VIEW, { lat: 37.2, lng: 127.2 }), "far"); // 광교 · 영통 쪽 멀리
});

test("지도 이동 값: PC 기본 레벨 5(250m) · 축소 +2 · 축소 0.3초 → panTo → 확대 0.3초", () => {
  assert.strictEqual(PC_MAP_LEVEL, 5);
  assert.strictEqual(PC_MOVE_OUT, 2);
  assert.deepStrictEqual(PC_MOVE_MS, { out: 300, back: 300 });
});

test("+ / − 단추: 가장 크게(레벨 1)면 + 끔, 가장 작게(레벨 14)면 − 끔", () => {
  assert.deepStrictEqual(pcZoomState(5, PC_ZOOM_MIN, PC_ZOOM_MAX), { zoomIn: true, zoomOut: true });
  assert.deepStrictEqual(pcZoomState(PC_ZOOM_MIN, PC_ZOOM_MIN, PC_ZOOM_MAX), { zoomIn: false, zoomOut: true });
  assert.deepStrictEqual(pcZoomState(PC_ZOOM_MAX, PC_ZOOM_MIN, PC_ZOOM_MAX), { zoomIn: true, zoomOut: false });
  assert.strictEqual(PC_ZOOM_MIN, 1);
  assert.strictEqual(PC_ZOOM_MAX, 14);
});

test("넘기기: 목록 순서대로, 처음 · 끝에서 멈춤", () => {
  const ids = [7, 3, 9];
  assert.strictEqual(pcStepId(ids, 7, 1), 3);
  assert.strictEqual(pcStepId(ids, 3, 1), 9);
  assert.strictEqual(pcStepId(ids, 9, 1), null); // 끝
  assert.strictEqual(pcStepId(ids, 7, -1), null); // 처음
  assert.strictEqual(pcStepId(ids, 9, -1), 3);
  assert.strictEqual(pcStepId(ids, 42, 1), 7); // 지금 행사가 목록에서 빠졌으면 첫 행사
  assert.strictEqual(pcStepId([], 7, 1), null);
  assert.strictEqual(pcStepId([5], 5, 1), null);
});

test("위치 점: 2~10개면 지금 것만 true, 10개 넘거나 1개뿐이면 점 없음", () => {
  assert.strictEqual(PC_DOTS_MAX, 10);
  assert.deepStrictEqual(pcPagerDots(2, 6), [false, false, true, false, false, false]);
  assert.strictEqual(pcPagerDots(0, 10).length, 10);
  assert.deepStrictEqual(pcPagerDots(0, 11), []); // 알약 "1 / 11" 만
  assert.deepStrictEqual(pcPagerDots(0, 1), []);
  assert.deepStrictEqual(pcPagerDots(0, 0), []);
});

test("위치 알약: '3 / 6', 행사가 없으면 빈 글", () => {
  assert.strictEqual(pcPosText(2, 6), "3 / 6");
  assert.strictEqual(pcPosText(29, 30), "30 / 30");
  assert.strictEqual(pcPosText(-1, 4), "1 / 4");
  assert.strictEqual(pcPosText(0, 0), "");
});

test("길찾기 창 자리: 버튼 위 가운데, 화면 양끝 · 위가 모자라면 안쪽 · 아래로", () => {
  const view = { width: 1280, height: 800 };
  const pop = { width: 300, height: 220 };
  // 버튼(가운데 x=600) 위 8px
  assert.deepStrictEqual(routePopPosition({ left: 560, top: 700, width: 80, bottom: 732 }, pop, view), {
    left: 450,
    top: 472,
  });
  // 왼쪽 끝 버튼 → 화면 안 8px
  assert.strictEqual(routePopPosition({ left: 0, top: 700, width: 80, bottom: 732 }, pop, view).left, 8);
  // 오른쪽 끝 버튼 → 화면 오른쪽 8px 안
  assert.strictEqual(routePopPosition({ left: 1250, top: 700, width: 30, bottom: 732 }, pop, view).left, 972);
  // 위 자리가 모자라면 버튼 아래
  assert.strictEqual(routePopPosition({ left: 560, top: 100, width: 80, bottom: 132 }, pop, view).top, 140);
});
