// 진행 날짜 표시 규칙 시험: node --test src/test/js/schedule-format.test.js
const test = require("node:test");
const assert = require("node:assert");
const { formatEventDates, formatScheduleDetail } = require("../../main/resources/static/schedule-format.js");

const NOW_2026 = Date.UTC(2026, 10, 2);

function range(start, end) {
  const out = [];
  for (let d = Date.parse(start); d <= Date.parse(end); d += 86400000) {
    out.push(new Date(d).toISOString().slice(0, 10));
  }
  return out;
}

function weekdays(start, end, days) {
  return range(start, end).filter((d) => days.includes(new Date(d).getUTCDay()));
}

function event(startDate, endDate, dates, extra) {
  return { startDate, endDate, dates, scheduleStatus: "DATES", ...extra };
}

const card = (e) => formatEventDates(e, NOW_2026);

test("날짜가 적으면 회차와 날짜를 모두 씀", () => {
  assert.strictEqual(card(event("2026-05-16", "2026-09-19", ["2026-05-16", "2026-09-19"])),
    "5/16 ~ 9/19 · 총 2회 · 5/16, 9/19");
});

test("기간 안의 그 요일이 빠짐없으면 요일로 요약", () => {
  assert.strictEqual(card(event("2026-05-01", "2026-11-01", weekdays("2026-05-01", "2026-11-01", [5, 6, 0]))),
    "5/1 ~ 11/1 · 금 · 토 · 일");
});

test("4일 이상 이어지는 요일은 줄임", () => {
  assert.strictEqual(card(event("2026-09-01", "2026-09-30", weekdays("2026-09-01", "2026-09-30", [2, 3, 4, 5, 6, 0]))),
    "9/1 ~ 9/30 · 화~일");
});

test("요일 하나만 빠짐없으면 그 요일만", () => {
  assert.strictEqual(card(event("2026-05-02", "2026-06-27", weekdays("2026-05-02", "2026-06-27", [6]))),
    "5/2 ~ 6/27 · 토요일만");
});

test("모든 날이면 매일", () => {
  assert.strictEqual(card(event("2026-05-04", "2026-05-31", range("2026-05-04", "2026-05-31"))), "5/4 ~ 5/31 · 매일");
});

test("빠진 날이 하나라도 있으면 요일로 안내하지 않음", () => {
  const dates = weekdays("2026-05-01", "2026-06-30", [6]).slice(1);
  dates.unshift("2026-05-03"); // 일요일이 섞여 "토 · 일" 이 될 수 있는 경우
  assert.strictEqual(card(event("2026-05-01", "2026-06-30", dates)), "5/1 ~ 6/30 · 총 9회");
});

test("모름은 기간만 표시", () => {
  assert.strictEqual(card({ startDate: "2026-05-04", endDate: "2026-05-31", scheduleStatus: "UNKNOWN", dates: [] }),
    "2026-05-04 ~ 2026-05-31");
  assert.strictEqual(card({ scheduleStatus: "UNKNOWN", dates: [] }), "기간 정보 없음");
});

test("예전 응답 모양이나 기간이 비어도 멈추지 않음", () => {
  assert.strictEqual(card({ startDate: "2026-05-04", endDate: "2026-05-31" }), "2026-05-04 ~ 2026-05-31");
  assert.strictEqual(card(event("2026-05-04", null, ["2026-05-04"])), "2026-05-04");
  assert.strictEqual(card(event(null, null, ["2026-05-04"])), "기간 정보 없음");
});

test("날짜가 기간 밖이면 믿지 않고 기간만 표시", () => {
  const e = event("2026-05-01", "2026-05-03", ["2026-05-16", "2026-09-19"], { scheduleHolidayNote: true, scheduleText: "원문" });
  assert.strictEqual(card(e), "2026-05-01 ~ 2026-05-03");
  assert.strictEqual(formatScheduleDetail(e), "일정 원문: 원문");
});

test("하루 행사는 지금 표시 그대로", () => {
  assert.strictEqual(card(event("2026-11-14", "2026-11-14", ["2026-11-14"])), "2026-11-14");
});

test("올해가 아니거나 해를 넘기면 연도까지", () => {
  assert.strictEqual(card(event("2025-05-16", "2025-09-19", ["2025-05-16", "2025-09-19"])),
    "2025-05-16 ~ 2025-09-19 · 총 2회 · 5/16, 9/19");
  assert.strictEqual(card(event("2026-12-20", "2027-01-10", ["2026-12-20", "2027-01-10"])),
    "2026-12-20 ~ 2027-01-10 · 총 2회 · 12/20, 1/10");
});

test("올해 계산은 한국 시간 기준", () => {
  const e = event("2027-05-16", "2027-09-19", ["2027-05-16", "2027-09-19"]);
  assert.match(formatEventDates(e, Date.UTC(2026, 11, 31, 14, 59)), /^2027-05-16/); // 한국 12/31 23:59
  assert.match(formatEventDates(e, Date.UTC(2026, 11, 31, 15, 0)), /^5\/16/); // 한국 1/1 00:00
});

test("공휴일 조건은 카드와 상세에 모두 남음", () => {
  const e = event("2026-05-01", "2026-11-01", weekdays("2026-05-01", "2026-11-01", [5, 6, 0]), { scheduleHolidayNote: true });
  assert.strictEqual(card(e), "5/1 ~ 11/1 · 금 · 토 · 일 · 공휴일 확인");
  assert.match(formatScheduleDetail(e), /공휴일은 원문 확인/);
});

test("상세는 달별 전체 목록과 원문", () => {
  const e = event("2026-05-02", "2026-06-13", weekdays("2026-05-02", "2026-06-13", [6]), { scheduleText: "매주 토요일" });
  assert.strictEqual(formatScheduleDetail(e), "진행일 7회\n5월: 2, 9, 16, 23, 30\n6월: 6, 13\n일정 원문: 매주 토요일");
});

test("상세의 같은 달이 두 해에 걸치면 나눠 씀", () => {
  const dates = ["2026-12-05", "2026-12-06", "2027-01-02", "2027-01-03", "2027-12-04", "2027-12-05"];
  const detail = formatScheduleDetail(event("2026-12-05", "2027-12-05", dates));
  assert.match(detail, /2026년 12월: 5, 6/);
  assert.match(detail, /2027년 12월: 4, 5/);
});

test("모름은 상세에 원문만", () => {
  assert.strictEqual(formatScheduleDetail({ scheduleStatus: "UNKNOWN", dates: [], scheduleText: "13:00~20:00" }),
    "일정 원문: 13:00~20:00");
  assert.strictEqual(formatScheduleDetail({ scheduleStatus: "UNKNOWN", dates: [] }), "");
});

// ===== "오늘 진행" · "이번 주말" 필터 =====
const { koreaToday, upcomingWeekend, filterDays, matchRunDays } = require("../../main/resources/static/schedule-format.js");

test("오늘은 한국 시간 기준", () => {
  assert.strictEqual(koreaToday(Date.UTC(2026, 10, 1, 14, 59)), "2026-11-01"); // 한국 11/1 23:59
  assert.strictEqual(koreaToday(Date.UTC(2026, 10, 1, 15, 0)), "2026-11-02"); // 한국 11/2 00:00
});

test("이번 주말은 오늘 이후 가장 가까운 토 · 일", () => {
  assert.deepStrictEqual(upcomingWeekend(Date.UTC(2026, 10, 2, 3)), ["2026-11-07", "2026-11-08"]); // 월
  assert.deepStrictEqual(upcomingWeekend(Date.UTC(2026, 10, 7, 3)), ["2026-11-07", "2026-11-08"]); // 토
  assert.deepStrictEqual(upcomingWeekend(Date.UTC(2026, 10, 8, 3)), ["2026-11-08"]); // 일: 오늘만
  assert.deepStrictEqual(upcomingWeekend(Date.UTC(2026, 11, 31, 3)), ["2027-01-02", "2027-01-03"]); // 해 넘김
  // 한국은 이미 토요일 00:30 인데 UTC 로는 금요일
  assert.deepStrictEqual(upcomingWeekend(Date.UTC(2026, 10, 6, 15, 30)), ["2026-11-07", "2026-11-08"]);
});

test("필터 없음이면 날짜 목록 없음", () => {
  assert.strictEqual(filterDays("all", NOW_2026), null);
  assert.deepStrictEqual(filterDays("today", NOW_2026), ["2026-11-02"]);
});

test("날짜 확정 행사는 그날 진행할 때만", () => {
  const e = event("2026-05-16", "2026-09-19", ["2026-05-16", "2026-09-19"]);
  assert.strictEqual(matchRunDays(e, ["2026-05-16"]), "yes");
  assert.strictEqual(matchRunDays(e, ["2026-05-23"]), "no"); // 기간 안이지만 진행일 아님 (2972 오안내 방지)
  assert.strictEqual(matchRunDays(e, ["2026-09-19", "2026-09-20"]), "yes");
});

test("모름 행사는 기간 안이면 숨기지 않고 일정 확인", () => {
  const e = { startDate: "2026-05-04", endDate: "2026-05-31", scheduleStatus: "UNKNOWN", dates: [] };
  assert.strictEqual(matchRunDays(e, ["2026-05-10"]), "maybe");
  assert.strictEqual(matchRunDays(e, ["2026-05-31"]), "maybe");
  assert.strictEqual(matchRunDays(e, ["2026-06-01"]), "no");
  assert.strictEqual(matchRunDays({ startDate: "2026-05-04", endDate: "2026-05-31" }, ["2026-05-10"]), "maybe"); // 예전 응답
});

test("날짜가 기간 밖이면 믿지 않고 모름처럼", () => {
  const e = event("2026-05-01", "2026-05-03", ["2026-05-16"]);
  assert.strictEqual(matchRunDays(e, ["2026-05-02"]), "maybe");
  assert.strictEqual(matchRunDays(e, ["2026-05-16"]), "no");
});

test("하루짜리 행사는 그날이면 확실", () => {
  assert.strictEqual(matchRunDays({ startDate: "2026-11-14", endDate: "2026-11-14", scheduleStatus: "UNKNOWN" }, ["2026-11-14"]), "yes");
  assert.strictEqual(matchRunDays({ startDate: "2026-11-14", endDate: null }, ["2026-11-14"]), "yes");
  assert.strictEqual(matchRunDays({ startDate: null, endDate: null }, ["2026-11-14"]), "no");
});
