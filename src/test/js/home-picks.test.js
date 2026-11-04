// 모바일 홈 칸별 고르기 규칙 시험 — 이번 주 · 곧 종료 · 곧 시작 (한국 시간 기준)
const test = require("node:test");
const assert = require("node:assert");
const {
  weekRange,
  weekLabel,
  thisWeekEvents,
  endingSoon,
  startingSoon,
} = require("../../main/resources/static/home-picks.js");

// 한국 시간 → 밀리초 (UTC 로 9시간 빼서)
function kst(y, m, d, h) {
  return Date.UTC(y, m - 1, d, (h || 12) - 9);
}
const WED = kst(2026, 11, 4); // 2026-11-04 수요일 12시

function ev(id, startDate, endDate, title) {
  return { id, title: title || "행사" + id, startDate, endDate };
}

test("이번 주: 한국 시간 월 ~ 일", () => {
  assert.deepStrictEqual(weekRange(WED), { start: "2026-11-02", end: "2026-11-08" });
  assert.deepStrictEqual(weekRange(kst(2026, 11, 2)), { start: "2026-11-02", end: "2026-11-08" }); // 월
});

test("이번 주: 일요일은 지난 월요일부터 그날까지", () => {
  assert.deepStrictEqual(weekRange(kst(2026, 11, 8)), { start: "2026-11-02", end: "2026-11-08" });
});

test("이번 주: 한국 시간 월요일 새벽(UTC 로는 일요일)도 새 주", () => {
  assert.deepStrictEqual(weekRange(kst(2026, 11, 9, 1)), { start: "2026-11-09", end: "2026-11-15" });
});

test("주 표시: 같은 달은 끝 날짜만, 달이 바뀌면 월까지", () => {
  assert.strictEqual(weekLabel({ start: "2026-11-02", end: "2026-11-08" }), "11월 2일 – 8일");
  assert.strictEqual(weekLabel({ start: "2026-10-26", end: "2026-11-01" }), "10월 26일 – 11월 1일");
  assert.strictEqual(weekLabel({ start: "2026-12-28", end: "2027-01-03" }), "12월 28일 – 1월 3일");
});

test("이번 주 행사: 기간이 겹치고 종료 안 된 것만, 날짜 없는 행사는 뺌", () => {
  const events = [
    ev(1, "2026-10-01", "2026-11-30"), // 운영중
    ev(2, "2026-11-07", "2026-11-07"), // 이번 주 토요일 하루
    ev(3, "2026-11-08", "2026-12-01"), // 일요일 시작 → 겹침
    ev(4, "2026-11-09", "2026-11-10"), // 다음 주 → 뺌
    ev(5, "2026-11-01", "2026-11-03"), // 어제 끝남 → 뺌 (이번 주와 겹쳐도)
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-11-20"), // 시작일만 없음 → 운영중으로 봄
  ];
  assert.deepStrictEqual(
    thisWeekEvents(events, WED).map((e) => e.id),
    [1, 2, 3, 7],
  );
});

test("곧 종료: 운영중만, 종료일 가까운 순 3개 (같은 날이면 제목 순)", () => {
  const events = [
    ev(1, "2026-10-01", "2026-12-31"),
    ev(2, "2026-11-01", "2026-11-04", "나 오늘 종료"),
    ev(3, "2026-11-01", "2026-11-04", "가 오늘 종료"),
    ev(4, "2026-11-05", "2026-11-06"), // 오픈예정 → 뺌
    ev(5, "2026-10-01", "2026-11-10"),
    ev(6, "2026-10-01", "2026-11-03"), // 종료 → 뺌
  ];
  assert.deepStrictEqual(
    endingSoon(events, WED).map((e) => e.id),
    [3, 2, 5],
  );
});

test("곧 시작: 오픈예정만, 시작일 가까운 순 3개", () => {
  const events = [
    ev(1, "2026-12-01", "2026-12-02"),
    ev(2, "2026-11-04", "2026-11-05"), // 오늘 시작 = 운영중 → 뺌
    ev(3, "2026-11-05", "2026-11-05"),
    ev(4, "2026-11-20", "2026-11-21"),
    ev(5, "2026-11-06", "2026-11-30"),
  ];
  assert.deepStrictEqual(
    startingSoon(events, WED).map((e) => e.id),
    [3, 5, 4],
  );
});

test("곧 종료 · 곧 시작: 해당 행사가 없으면 빈 배열", () => {
  assert.deepStrictEqual(endingSoon([ev(1, "2026-12-01", "2026-12-02")], WED), []);
  assert.deepStrictEqual(startingSoon([ev(1, "2026-10-01", "2026-11-30")], WED), []);
});
