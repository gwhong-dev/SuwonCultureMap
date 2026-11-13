// PC 검색 · 분류 규칙 시험 (11/13 확정 시안 R4 A · R5 A4 — docs/design/2026-11-13-pc-pending.md "② 검색 · 분류")
const test = require("node:test");
const assert = require("node:assert");
const {
  PC_CATEGORY_MENU,
  PC_SEARCH_MAP_DELAY,
  pcCategoryCounts,
  pcCategoryRows,
  pcSearchMapMove,
  pcCategoryKey,
} = require("../../main/resources/static/pc-search.js");

test("펼침 목록 순서: 전체 분류 · 행사 · 공연 · 전시 · 축제 · 기타 · 찜한 행사만", () => {
  assert.deepStrictEqual(PC_CATEGORY_MENU, ["all", "행사", "공연", "전시", "축제", "기타", "찜"]);
});

test("펼침 목록 줄: 행사 데이터에 없는 분류는 빼고, 전체 · 찜은 늘 (스포츠처럼 메뉴에 없는 분류는 안 보임)", () => {
  assert.deepStrictEqual(pcCategoryRows(["all", "공연", "찜", "스포츠", "축제"]), ["all", "공연", "축제", "찜"]);
  assert.deepStrictEqual(pcCategoryRows(["all"]), ["all", "찜"]);
});

test("분류 개수: 다른 조건을 통과한 행사 중 그 분류 수, 전체 = 모두, 찜 = 찜한 행사 수", () => {
  const events = [
    { id: 1, category: "공연" },
    { id: 2, category: "공연" },
    { id: 3, category: "전시" },
    { id: 4, category: "축제" },
    { id: 5, category: "스포츠" }, // 메뉴에 없는 분류 → 전체에만
  ];
  const favs = new Set([2, 4]);
  const counts = pcCategoryCounts(events, (id) => favs.has(id));
  assert.deepStrictEqual(counts, { all: 5, 행사: 0, 공연: 2, 전시: 1, 축제: 1, 기타: 0, 찜: 2 });
});

test("분류 개수: 찜은 '찜' 을 골랐을 때 조건의 행사로 셈 (진행 탭이 안 걸려 더 많을 수 있음)", () => {
  const open = [{ id: 1, category: "공연" }];
  const favBase = [{ id: 1, category: "공연" }, { id: 2, category: "전시" }]; // 2 = 진행 예정이라 '진행 중' 탭에선 빠짐
  const counts = pcCategoryCounts(open, () => true, favBase);
  assert.strictEqual(counts.all, 1);
  assert.strictEqual(counts["찜"], 2);
});

test("분류 개수: 행사가 없으면 모두 0", () => {
  assert.deepStrictEqual(pcCategoryCounts([], () => false), { all: 0, 행사: 0, 공연: 0, 전시: 0, 축제: 0, 기타: 0, 찜: 0 });
});

test("검색 지도 이동: 결과 0개 → 지도 그대로", () => {
  assert.deepStrictEqual(pcSearchMapMove([]), { kind: "none" });
  assert.deepStrictEqual(pcSearchMapMove([{ latitude: null, longitude: null }]), { kind: "none" });
});

test("검색 지도 이동: 결과 1개 → 그 행사로", () => {
  assert.deepStrictEqual(pcSearchMapMove([{ latitude: 37.28, longitude: 127.01 }]), { kind: "center", lat: 37.28, lng: 127.01 });
});

test("검색 지도 이동: 같은 장소 여러 행사 → 그 한 곳으로 (setBounds 하면 너무 확대됨)", () => {
  const same = { latitude: 37.28, longitude: 127.01 };
  assert.deepStrictEqual(pcSearchMapMove([same, { ...same }, { ...same }]), { kind: "center", lat: 37.28, lng: 127.01 });
});

test("검색 지도 이동: 여러 곳 → 결과 핀이 모두 보이게 (겹친 자리는 한 번만)", () => {
  const move = pcSearchMapMove([
    { latitude: 37.28, longitude: 127.01 },
    { latitude: 37.3, longitude: 127.05 },
    { latitude: 37.28, longitude: 127.01 },
  ]);
  assert.deepStrictEqual(move, {
    kind: "bounds",
    points: [
      { lat: 37.28, lng: 127.01 },
      { lat: 37.3, lng: 127.05 },
    ],
  });
});

test("검색 지도 이동은 치는 동안 300ms 쉬면", () => {
  assert.strictEqual(PC_SEARCH_MAP_DELAY, 300);
});

test("분류 칩 키보드: 닫혀 있을 때 Enter · Space · ↓ 로 열기, 다른 키는 그대로", () => {
  for (const key of ["Enter", " ", "ArrowDown"]) {
    assert.deepStrictEqual(pcCategoryKey(key, false, 2, 7), { action: "open", index: 2 });
  }
  assert.strictEqual(pcCategoryKey("a", false, 0, 7).action, "none");
  assert.strictEqual(pcCategoryKey("Escape", false, 0, 7).action, "none");
});

test("펼침 목록 키보드: ↑ ↓ 이동(끝에서 멈춤) · Home · End · Enter 고르기 · Esc 닫기", () => {
  assert.deepStrictEqual(pcCategoryKey("ArrowDown", true, 0, 7), { action: "move", index: 1 });
  assert.deepStrictEqual(pcCategoryKey("ArrowDown", true, 6, 7), { action: "move", index: 6 });
  assert.deepStrictEqual(pcCategoryKey("ArrowUp", true, 0, 7), { action: "move", index: 0 });
  assert.deepStrictEqual(pcCategoryKey("ArrowUp", true, 3, 7), { action: "move", index: 2 });
  assert.deepStrictEqual(pcCategoryKey("Home", true, 4, 7), { action: "move", index: 0 });
  assert.deepStrictEqual(pcCategoryKey("End", true, 1, 7), { action: "move", index: 6 });
  assert.deepStrictEqual(pcCategoryKey("Enter", true, 3, 7), { action: "pick", index: 3 });
  assert.deepStrictEqual(pcCategoryKey(" ", true, 3, 7), { action: "pick", index: 3 });
  assert.deepStrictEqual(pcCategoryKey("Escape", true, 3, 7), { action: "close", index: 3 });
  assert.deepStrictEqual(pcCategoryKey("Tab", true, 3, 7), { action: "close", index: 3 });
});
