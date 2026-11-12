// PC 지도 하단 카드 규칙 시험 (11/13 확정 시안 P1) — 배지 · 기간 줄 · 장소 줄 · 마커 튀어오름 · 키보드 · 길찾기 창 자리
const test = require("node:test");
const assert = require("node:assert");
const {
  pcCardBadges,
  pcCardPeriod,
  pcCardPlace,
  shouldHopMarker,
  pcCardKeyAction,
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

test("키보드: 카드에 포커스가 있을 때 Enter = 상세 · Space = 찜, 카드 안 버튼 · 조합키 · 처리된 키는 그대로", () => {
  assert.strictEqual(pcCardKeyAction({ key: "Enter", onCard: true }), "detail");
  assert.strictEqual(pcCardKeyAction({ key: " ", onCard: true }), "favorite");
  assert.strictEqual(pcCardKeyAction({ key: "Enter", onCard: false }), null); // 버튼 기본 동작
  assert.strictEqual(pcCardKeyAction({ key: " ", onCard: true, alt: true }), null);
  assert.strictEqual(pcCardKeyAction({ key: "Enter", onCard: true, handled: true }), null);
  assert.strictEqual(pcCardKeyAction({ key: "ArrowRight", onCard: true }), null); // ← → 는 strip-nav.js
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
