// 진행 날짜 표시 규칙 시험: node --test src/test/js/schedule-format.test.js
const test = require("node:test");
const assert = require("node:assert");
const {
  formatEventDates,
  formatScheduleDetail,
  scheduleRowText,
  eventStatus,
  statusLabel,
  ddayText,
} = require("../../main/resources/static/schedule-format.js");

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

// ===== 합치기 대표의 확인된 회차 (설계 8번) =====
test("날짜 모르는 대표는 숨긴 글의 회차를 표시만", () => {
  const e = { scheduleStatus: "UNKNOWN", startDate: "2026-11-01", endDate: "2026-12-31", dates: [],
    confirmedDates: ["2026-11-01", "2026-11-08"], scheduleText: "~12/31" };
  assert.strictEqual(formatScheduleDetail(e), "확인된 회차: 11/1, 11/8\n일정 원문: ~12/31");
  assert.strictEqual(card(e), "2026-11-01 ~ 2026-12-31"); // 카드 · 기간은 그대로 ("그날만"으로 안내하지 않음)
});

test("확인된 회차가 두 해에 걸치면 연도까지", () => {
  const e = { scheduleStatus: "UNKNOWN", startDate: "2026-12-01", endDate: "2027-01-31", dates: [],
    confirmedDates: ["2026-12-31", "2027-01-01"] };
  assert.strictEqual(formatScheduleDetail(e), "확인된 회차: 2026-12-31, 2027-01-01");
});

test("대표 날짜가 있으면 확인된 회차는 쓰지 않음", () => {
  const e = event("2026-11-01", "2026-11-08", ["2026-11-01", "2026-11-08"], { confirmedDates: ["2026-11-01"] });
  assert.doesNotMatch(formatScheduleDetail(e), /확인된 회차/);
});

test("확인된 회차는 필터에서 모름과 같음", () => {
  const { matchRunDays, calendarDateChoices } = require("../../main/resources/static/schedule-format.js");
  const e = { scheduleStatus: "UNKNOWN", startDate: "2026-11-01", endDate: "2026-12-31", dates: [],
    confirmedDates: ["2026-11-08"] };
  assert.strictEqual(matchRunDays(e, ["2026-11-08"]), "maybe");
  // 11/1 ~ 12/31 = 61일 긴 행사 → 확인된 회차가 아니라 기간 안 하루 (오늘 11/2 부터)
  const c = calendarDateChoices(e, NOW_2026);
  assert.strictEqual(c.selected, "2026-11-02");
  assert.ok(!c.dates.includes("2026-11-01"));
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

test("+7일은 오늘 포함 7일 (한국 시간, 달 · 해 넘김)", () => {
  assert.deepStrictEqual(filterDays("week", Date.UTC(2026, 10, 2, 3)), [
    "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07", "2026-11-08",
  ]);
  const yearEnd = filterDays("week", Date.UTC(2026, 11, 28, 3));
  assert.strictEqual(yearEnd.length, 7);
  assert.strictEqual(yearEnd[0], "2026-12-28");
  assert.strictEqual(yearEnd[6], "2027-01-03");
  // 한국은 이미 11/3 00:30 인데 UTC 로는 11/2
  assert.strictEqual(filterDays("week", Date.UTC(2026, 10, 2, 15, 30))[0], "2026-11-03");
});

test("+7일 필터: 기간이 7일 안에 걸치면 나옴", () => {
  const days = filterDays("week", Date.UTC(2026, 10, 2, 3)); // 11/2 ~ 11/8
  assert.strictEqual(matchRunDays(event("2026-11-08", "2026-11-08"), days), "yes"); // 마지막 날 하루짜리
  assert.strictEqual(matchRunDays(event("2026-11-09", "2026-11-09"), days), "no"); // 8일째
  assert.strictEqual(matchRunDays(event("2026-10-01", "2026-12-31"), days), "maybe"); // 긴 기간 · 날짜 모름
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

// ===== 캘린더 저장 날짜 고르기 =====
const { calendarDateChoices } = require("../../main/resources/static/schedule-format.js");

test("캘린더 날짜는 오늘 이후 진행일, 기본값은 가장 가까운 날", () => {
  const e = event("2026-10-03", "2026-11-28", weekdays("2026-10-03", "2026-11-28", [6]));
  const c = calendarDateChoices(e, Date.UTC(2026, 10, 1, 15, 30)); // 한국 11/2(월) 00:30
  assert.deepStrictEqual(c.dates, ["2026-11-07", "2026-11-14", "2026-11-21", "2026-11-28"]);
  assert.strictEqual(c.selected, "2026-11-07");
  assert.strictEqual(c.labels[0], "11/7 (토)");
  // 오늘이 진행일이면 오늘도 포함
  assert.strictEqual(calendarDateChoices(e, Date.UTC(2026, 10, 7, 3)).selected, "2026-11-07");
});

test("진행일이 다 지났으면 전체를 보여 주고 마지막 날", () => {
  const e = event("2025-05-16", "2025-09-19", ["2025-05-16", "2025-09-19"]);
  const c = calendarDateChoices(e, NOW_2026);
  assert.deepStrictEqual(c.dates, ["2025-05-16", "2025-09-19"]);
  assert.strictEqual(c.selected, "2025-09-19");
  assert.strictEqual(c.labels[1], "2025-09-19 (금)"); // 올해가 아니면 연도까지
});

test("날짜 모름 · 하루 행사 · 기간 밖 날짜는 고르지 않음 (지금처럼 기간 전체)", () => {
  assert.strictEqual(calendarDateChoices({ startDate: "2026-11-01", endDate: "2026-11-30", scheduleStatus: "UNKNOWN", dates: [] }, NOW_2026), null);
  assert.strictEqual(calendarDateChoices(event("2026-11-14", "2026-11-14", ["2026-11-14"]), NOW_2026), null);
  assert.strictEqual(calendarDateChoices(event("2026-11-01", "2026-11-03", ["2026-11-16"]), NOW_2026), null);
});

// ===== 기간이 30일을 넘는 긴 행사: 고른 하루 (11/11 사용자 결정) =====
const { isLongEvent } = require("../../main/resources/static/schedule-format.js");
const longEvent = (start, end) => ({ scheduleStatus: "UNKNOWN", startDate: start, endDate: end, dates: [] });
const KST_1102 = Date.UTC(2026, 10, 2, 3); // 한국 11/2 12:00

test("긴 행사 경계: 29 · 30일은 기간 전체, 31일부터 고른 하루", () => {
  assert.strictEqual(isLongEvent(longEvent("2026-11-01", "2026-11-29")), false);
  assert.strictEqual(isLongEvent(longEvent("2026-11-01", "2026-11-30")), false);
  assert.strictEqual(isLongEvent(longEvent("2026-11-01", "2026-12-01")), true);
  assert.strictEqual(calendarDateChoices(longEvent("2026-11-01", "2026-11-29"), KST_1102), null);
  assert.strictEqual(calendarDateChoices(longEvent("2026-11-01", "2026-11-30"), KST_1102), null);
  assert.ok(calendarDateChoices(longEvent("2026-11-01", "2026-12-01"), KST_1102));
  assert.strictEqual(isLongEvent(longEvent("2026-11-01", null)), false);
  assert.strictEqual(isLongEvent(longEvent("2026-12-01", "2026-11-01")), false); // 뒤바뀐 원본
});

test("긴 행사 기본값: 진행 중이면 오늘, 남은 날부터 목록", () => {
  const c = calendarDateChoices(longEvent("2026-11-01", "2026-12-01"), KST_1102);
  assert.strictEqual(c.selected, "2026-11-02");
  assert.strictEqual(c.dates[0], "2026-11-02");
  assert.strictEqual(c.dates[c.dates.length - 1], "2026-12-01");
  assert.strictEqual(c.dates.length, 30);
  assert.strictEqual(c.labels[0], "11/2 (월)");
  // 마지막 날 = 오늘이면 하루만
  assert.deepStrictEqual(calendarDateChoices(longEvent("2026-10-01", "2026-11-02"), KST_1102).dates, ["2026-11-02"]);
});

test("긴 행사 기본값: 시작 전 · 끝난 행사는 시작일", () => {
  const before = calendarDateChoices(longEvent("2026-12-10", "2027-03-01"), KST_1102);
  assert.strictEqual(before.selected, "2026-12-10");
  assert.strictEqual(before.dates[0], "2026-12-10");
  assert.strictEqual(before.labels.find((l, i) => before.dates[i] === "2027-01-01"), "2027-01-01 (금)");
  const ended = calendarDateChoices(longEvent("2026-01-01", "2026-10-31"), KST_1102);
  assert.strictEqual(ended.selected, "2026-01-01");
});

test("긴 행사 목록은 최대 90일 (몇 년짜리 상설도)", () => {
  const c = calendarDateChoices(longEvent("2020-01-01", "2030-12-31"), KST_1102);
  assert.strictEqual(c.dates.length, 90);
  assert.strictEqual(c.dates[0], "2026-11-02");
  assert.strictEqual(c.dates[89], "2027-01-30");
});

test("진행 날짜가 있는 긴 행사는 진행일만 (긴 행사 규칙보다 먼저)", () => {
  const e = event("2026-10-03", "2026-11-28", weekdays("2026-10-03", "2026-11-28", [6]));
  assert.deepStrictEqual(calendarDateChoices(e, KST_1102).dates, ["2026-11-07", "2026-11-14", "2026-11-21", "2026-11-28"]);
});

// ===== 상태 배지 · D-day (시작일 · 종료일만으로, 오늘은 한국 시간) =====
// 2026-11-02 12:00 KST (= 03:00 UTC)
const NOW_KST_NOON = Date.UTC(2026, 10, 2, 3);
const period = (startDate, endDate) => ({ startDate, endDate });

test("오늘이 기간 안이면 운영중, 전이면 오픈예정, 후면 종료", () => {
  assert.strictEqual(eventStatus(period("2026-11-01", "2026-11-30"), NOW_KST_NOON), "open");
  assert.strictEqual(eventStatus(period("2026-11-20", "2026-11-22"), NOW_KST_NOON), "upcoming");
  assert.strictEqual(eventStatus(period("2026-09-01", "2026-10-20"), NOW_KST_NOON), "ended");
});

test("시작일 · 종료일 당일은 운영중", () => {
  assert.strictEqual(eventStatus(period("2026-11-02", "2026-11-10"), NOW_KST_NOON), "open");
  assert.strictEqual(eventStatus(period("2026-10-20", "2026-11-02"), NOW_KST_NOON), "open");
});

test("종료일이 없으면 시작일 하루 행사로, 둘 다 없으면 배지 없음", () => {
  assert.strictEqual(eventStatus(period("2026-11-02", null), NOW_KST_NOON), "open");
  assert.strictEqual(eventStatus(period("2026-11-01", null), NOW_KST_NOON), "ended");
  assert.strictEqual(eventStatus({}, NOW_KST_NOON), null);
  assert.strictEqual(ddayText({}, NOW_KST_NOON), null);
});

test("시작일만 없으면 이미 시작한 것으로 보고 종료일까지 운영중 (지난 행사 판별과 같은 기준)", () => {
  assert.strictEqual(eventStatus(period(null, "2026-11-10"), NOW_KST_NOON), "open");
  assert.strictEqual(ddayText(period(null, "2026-11-10"), NOW_KST_NOON), "종료까지 D-8");
  assert.strictEqual(eventStatus(period(null, "2026-10-20"), NOW_KST_NOON), "ended");
});

test("상태는 한국 시간 기준 (UTC 로는 전날 밤)", () => {
  const kst0030 = Date.UTC(2026, 10, 2, 15, 30); // 11/3 00:30 KST
  assert.strictEqual(eventStatus(period("2026-11-03", "2026-11-03"), kst0030), "open");
  assert.strictEqual(eventStatus(period("2026-11-02", "2026-11-02"), kst0030), "ended");
});

test("운영중은 종료까지, 오픈예정은 시작까지 남은 날", () => {
  assert.strictEqual(ddayText(period("2026-11-01", "2026-11-30"), NOW_KST_NOON), "종료까지 D-28");
  assert.strictEqual(ddayText(period("2026-11-20", "2026-11-22"), NOW_KST_NOON), "D-18");
  assert.strictEqual(ddayText(period("2026-11-03", "2026-11-03"), NOW_KST_NOON), "D-1");
});

test("마지막 날은 오늘 종료, 끝난 행사는 D-day 없음", () => {
  assert.strictEqual(ddayText(period("2026-10-20", "2026-11-02"), NOW_KST_NOON), "오늘 종료");
  assert.strictEqual(ddayText(period("2026-11-02", null), NOW_KST_NOON), "오늘 종료");
  assert.strictEqual(ddayText(period("2026-09-01", "2026-10-20"), NOW_KST_NOON), null);
});

test("배지 글자", () => {
  assert.strictEqual(statusLabel("open"), "운영중");
  assert.strictEqual(statusLabel("upcoming"), "오픈예정");
  assert.strictEqual(statusLabel("ended"), "종료");
  assert.strictEqual(statusLabel(null), "");
});

// 모바일 상세 "진행" 줄: 일정 원문 한 줄이 시간 줄과 같으면 빼서 두 번 안 보이게 (11/10 제보 ?event=6)
test("scheduleRowText: 일정 원문 = 시간 줄이면 빈 값", () => {
  const e = { startDate: "2026-11-01", endDate: "2026-11-30", scheduleText: "13:00~18:00" };
  assert.strictEqual(scheduleRowText(e, "13:00 ~ 18:00"), "");
});

test("scheduleRowText: 일정 원문이 시간 줄과 다르거나 시간 줄이 없으면 유지", () => {
  const e = { startDate: "2026-11-01", endDate: "2026-11-30", scheduleText: "매주 토 14:00" };
  assert.strictEqual(scheduleRowText(e, "13:00~18:00"), "일정 원문: 매주 토 14:00");
  assert.strictEqual(scheduleRowText(e, ""), "일정 원문: 매주 토 14:00");
});

test("scheduleRowText: 진행일 · 확인된 회차가 있으면 시간과 같아도 유지", () => {
  const dates = range("2026-11-01", "2026-11-20");
  const e = { startDate: "2026-11-01", endDate: "2026-11-20", scheduleStatus: "DATES", dates, scheduleText: "13:00~18:00" };
  assert.match(scheduleRowText(e, "13:00~18:00"), /진행일 20회/);
  const c = { startDate: "2026-11-01", endDate: "2026-11-20", confirmedDates: ["2026-11-03"], scheduleText: "13:00~18:00" };
  assert.match(scheduleRowText(c, "13:00~18:00"), /확인된 회차/);
});
