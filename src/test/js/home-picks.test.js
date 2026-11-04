// 모바일 홈 칸별 고르기 규칙 시험 — 이번 주 · 곧 종료 · 곧 시작 (한국 시간 기준)
const test = require("node:test");
const assert = require("node:assert");
const {
  weekRange,
  weekLabel,
  thisWeekEvents,
  endingSoon,
  startingSoon,
  carouselPicks,
  monthRange,
  monthTitle,
  monthEvents,
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

test("이번 주: 해를 넘기는 주", () => {
  assert.deepStrictEqual(weekRange(kst(2027, 1, 1)), { start: "2026-12-28", end: "2027-01-03" });
});

test("곧 종료 · 곧 시작: 날짜 없는 행사는 빼고, 한쪽 날짜만 있는 행사는 그 날 기준", () => {
  const events = [
    ev(1, null, null), // 날짜 없음 → 둘 다 뺌
    ev(2, "2026-11-04", null), // 시작일만 = 오늘 하루 → 운영중, 오늘 종료
    ev(3, null, "2026-11-10"), // 종료일만 → 운영중
    ev(4, "2026-11-06", null), // 시작일만(앞으로) → 오픈예정
    ev(5, "2026-10-01", "2026-11-05"),
  ];
  assert.deepStrictEqual(endingSoon(events, WED).map((e) => e.id), [2, 5, 3]);
  assert.deepStrictEqual(startingSoon(events, WED).map((e) => e.id), [4]);
});

// 캐러셀용: 사진 주소까지 넣은 행사
function pic(id, startDate, endDate, title) {
  return { ...ev(id, startDate, endDate, title), imageUrl: "https://example.com/" + id + ".jpg" };
}

test("캐러셀: 이번 주 행사 중 사진 있는 것만, 운영중 먼저 → 종료일 가까운 순", () => {
  const events = [
    pic(1, "2026-11-06", "2026-11-07"), // 오픈예정 (이번 주 금 ~ 토)
    pic(2, "2026-10-01", "2026-12-31"), // 운영중, 종료 늦음
    pic(3, "2026-11-01", "2026-11-05"), // 운영중, 종료 빠름
    ev(4, "2026-11-01", "2026-11-04"), // 사진 없음 → 뺌
    pic(5, "2026-11-09", "2026-11-10"), // 다음 주 → 뺌
    pic(6, "2026-11-01", "2026-11-03"), // 종료 → 뺌
    pic(7, "2026-11-05", "2026-11-05"), // 오픈예정, 1번보다 빠름
  ];
  assert.deepStrictEqual(
    carouselPicks(events, WED).map((e) => e.id),
    [3, 2, 7, 1],
  );
});

test("캐러셀: 최대 5개, 같은 종료일이면 제목 순", () => {
  const events = [6, 5, 4, 3, 2, 1].map((n) =>
    pic(n, "2026-10-01", "2026-11-30", "행사" + n),
  );
  assert.deepStrictEqual(
    carouselPicks(events, WED).map((e) => e.id),
    [1, 2, 3, 4, 5],
  );
});

test("캐러셀: 사진 주소가 빈칸 · 공백이면 사진 없음으로 봄", () => {
  const events = [
    { ...ev(1, "2026-10-01", "2026-11-30"), imageUrl: "" },
    { ...ev(2, "2026-10-01", "2026-11-30"), imageUrl: "  " },
    { ...ev(3, "2026-10-01", "2026-11-30"), imageUrl: null },
  ];
  assert.deepStrictEqual(carouselPicks(events, WED), []);
});

test("캐러셀: 종료일 없는 오픈예정은 시작일로 순서", () => {
  const events = [
    pic(1, "2026-11-07", null), // 토요일 하루
    pic(2, "2026-11-06", "2026-11-20"),
  ];
  assert.deepStrictEqual(carouselPicks(events, WED).map((e) => e.id), [1, 2]);
});

test("이달: 한국 시간 1일 ~ 말일 (말일 새벽도 그달, 1일 새벽은 새 달)", () => {
  assert.deepStrictEqual(monthRange(WED), { start: "2026-11-01", end: "2026-11-30" });
  assert.deepStrictEqual(monthRange(kst(2026, 12, 1, 1)), { start: "2026-12-01", end: "2026-12-31" }); // UTC 로는 11/30
  assert.deepStrictEqual(monthRange(kst(2028, 2, 10)), { start: "2028-02-01", end: "2028-02-29" }); // 윤년
});

test("이달 제목: \"11월의 수원 행사\"", () => {
  assert.strictEqual(monthTitle(WED), "11월의 수원 행사");
  assert.strictEqual(monthTitle(kst(2027, 1, 1, 0)), "1월의 수원 행사");
});

test("이달 행사: 이번 달과 겹치고 종료 안 된 것만, 시작일 순", () => {
  const events = [
    ev(1, "2026-11-20", "2026-11-25"), // 오픈예정, 이달
    ev(2, "2026-10-01", "2026-11-10"), // 운영중, 지난달 시작
    ev(3, "2026-11-01", "2026-11-03"), // 종료 → 뺌
    ev(4, "2026-12-01", "2026-12-05"), // 다음 달 시작 → 뺌
    ev(5, "2026-11-30", null), // 말일 하루
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-11-15"), // 시작일 없이 끝만 → 운영중, 종료일로 순서
  ];
  assert.deepStrictEqual(monthEvents(events, WED).map((e) => e.id), [2, 7, 1, 5]);
});

test("이달 행사: 최대 10개, 같은 시작일이면 제목 순", () => {
  const events = Array.from({ length: 12 }, (_, i) =>
    ev(12 - i, "2026-11-10", "2026-11-20", "행사" + String(12 - i).padStart(2, "0")),
  );
  assert.deepStrictEqual(
    monthEvents(events, WED).map((e) => e.id),
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  );
});
