// 목록 위 날짜 줄 · 달력 · PC 진행 탭 규칙 시험 (calendar-strip.js)
const test = require("node:test");
const assert = require("node:assert");
const {
  STRIP_DAYS,
  isDayValue,
  weekdayOf,
  stripDays,
  monthCells,
  shiftMonth,
  monthOf,
  countRunDays,
  dotLevel,
  highlightedDays,
  nextDayFilter,
  dayTitle,
  dateFilterTitle,
  matchesStatusTab,
  statusTabApplies,
  isPastDay,
  singleDay,
  pastSelectedDay,
  hidesEndedEvents,
  shortDayTitle,
  pastDayNote,
  monthStepLabel,
} = require("../../main/resources/static/calendar-strip.js");
const { filterDays, matchRunDays } = require("../../main/resources/static/schedule-format.js");

// 2026-11-11(수) 한국 시간 낮 12시
const NOW = Date.UTC(2026, 10, 11, 3, 0, 0);

test("날짜 값 판별: 2026-11-14 꼴만", () => {
  assert.ok(isDayValue("2026-11-14"));
  assert.ok(!isDayValue("today"));
  assert.ok(!isDayValue("all"));
  assert.ok(!isDayValue(""));
  assert.ok(!isDayValue(undefined));
});

test("요일: 2026-11-01 은 일요일, 11-14 는 토요일", () => {
  assert.strictEqual(weekdayOf("2026-11-01"), 0);
  assert.strictEqual(weekdayOf("2026-11-14"), 6);
});

test("filterDays 가 날짜 하나도 받음 (기존 today · weekend · week 그대로)", () => {
  assert.deepStrictEqual(filterDays("2026-11-14", NOW), ["2026-11-14"]);
  assert.deepStrictEqual(filterDays("today", NOW), ["2026-11-11"]);
  assert.deepStrictEqual(filterDays("weekend", NOW), ["2026-11-14", "2026-11-15"]);
  assert.strictEqual(filterDays("week", NOW).length, 7);
  assert.strictEqual(filterDays("all", NOW), null);
});

test("날짜 줄: 오늘부터 2주, 첫 칸은 '오늘', 월말을 넘어감", () => {
  const days = stripDays("2026-11-25", STRIP_DAYS);
  assert.strictEqual(days.length, 14);
  assert.deepStrictEqual(days[0], { date: "2026-11-25", label: "오늘", num: 25, weekday: 3 });
  assert.strictEqual(days[1].label, "목");
  assert.strictEqual(days[6].date, "2026-12-01");
  assert.strictEqual(days[6].num, 1);
});

test("한 달 달력: 1일 앞은 빈 칸, 그 달 날짜 수만큼", () => {
  const nov = monthCells(2026, 11); // 11/1 일요일 → 빈 칸 없음
  assert.strictEqual(nov[0], "2026-11-01");
  assert.strictEqual(nov.length, 30);
  const dec = monthCells(2026, 12); // 12/1 화요일 → 빈 칸 2개
  assert.deepStrictEqual(dec.slice(0, 3), [null, null, "2026-12-01"]);
  assert.strictEqual(dec[dec.length - 1], "2026-12-31");
  assert.strictEqual(monthCells(2028, 2).filter(Boolean).length, 29); // 윤년
});

test("달 옮기기: 12월 → 다음 해 1월, 1월 → 지난해 12월", () => {
  assert.deepStrictEqual(shiftMonth({ year: 2026, month: 12 }, 1), { year: 2027, month: 1 });
  assert.deepStrictEqual(shiftMonth({ year: 2027, month: 1 }, -1), { year: 2026, month: 12 });
  assert.deepStrictEqual(monthOf("2026-11-14"), { year: 2026, month: 11 });
});

test("날짜별 행사 수: 진행 날짜가 있는 행사 · 기간 행사 · 종료일 없는 행사 (matchRunDays 그대로)", () => {
  const events = [
    // 진행 날짜(schedule)가 정해진 행사: 그 날만
    { startDate: "2026-11-12", endDate: "2026-11-20", scheduleStatus: "DATES", dates: ["2026-11-14", "2026-11-15"] },
    // 기간 행사(날짜 모름) = 기간 안 모든 날 ("maybe" 도 목록에 보이니 셈)
    { startDate: "2026-11-01", endDate: "2026-11-14" },
    // 종료일 없는 행사 = 시작일 하루
    { startDate: "2026-11-15", endDate: null },
    // 날짜 없는 행사 = 어느 날에도 안 셈
    { startDate: null, endDate: null },
  ];
  const counts = countRunDays(events, ["2026-11-13", "2026-11-14", "2026-11-15", "2026-11-16"]);
  assert.deepStrictEqual(counts, { "2026-11-13": 1, "2026-11-14": 2, "2026-11-15": 2, "2026-11-16": 0 });
  // 목록 필터와 같은 판정
  assert.strictEqual(matchRunDays(events[0], ["2026-11-13"]), "no");
  assert.strictEqual(matchRunDays(events[1], ["2026-11-13"]), "maybe");
});

test("달력 점: 0 / 1~2 / 3~5 / 6 이상", () => {
  assert.deepStrictEqual([0, 1, 2, 3, 5, 6, 40].map(dotLevel), [0, 1, 1, 2, 2, 3, 3]);
});

test("칠할 날짜: 날짜 하나 · 홈 '이번 주말' · '+7일' 은 여러 칸, 필터 없으면 없음", () => {
  assert.deepStrictEqual(highlightedDays("2026-11-14", NOW), ["2026-11-14"]);
  assert.deepStrictEqual(highlightedDays("weekend", NOW), ["2026-11-14", "2026-11-15"]);
  assert.strictEqual(highlightedDays("week", NOW).length, 7);
  assert.deepStrictEqual(highlightedDays("all", NOW), []);
});

test("날짜 누르기: 고른 날 다시 누르면 해제, 다른 날 · 주말 범위 안 날은 그날 하나로", () => {
  assert.strictEqual(nextDayFilter("all", "2026-11-14", NOW), "2026-11-14");
  assert.strictEqual(nextDayFilter("2026-11-14", "2026-11-14", NOW), "all");
  assert.strictEqual(nextDayFilter("2026-11-14", "2026-11-15", NOW), "2026-11-15");
  assert.strictEqual(nextDayFilter("weekend", "2026-11-14", NOW), "2026-11-14");
  assert.strictEqual(nextDayFilter("today", "2026-11-11", NOW), "all"); // 홈 '오늘' 로 켠 것도 오늘 칸으로 해제
});

test("제목 글자: 날짜 · 오늘 · 이번 주말 · 7일 안, 필터 없으면 null", () => {
  assert.strictEqual(dayTitle("2026-11-14"), "11월 14일 (토)");
  assert.strictEqual(dateFilterTitle("2026-11-14", NOW), "11월 14일 (토)");
  assert.strictEqual(dateFilterTitle("2026-11-11", NOW), "오늘 11월 11일 (수)");
  assert.strictEqual(dateFilterTitle("today", NOW), "오늘 11월 11일 (수)");
  assert.strictEqual(dateFilterTitle("weekend", NOW), "이번 주말");
  assert.strictEqual(dateFilterTitle("week", NOW), "7일 안");
  assert.strictEqual(dateFilterTitle("all", NOW), null);
});

test("PC 탭: 진행 중 + 진행 예정 = 지난 행사 뺀 전체 (날짜 없는 행사는 진행 중, 종료는 어느 탭에도 안 셈)", () => {
  const open = { startDate: "2026-11-01", endDate: "2026-11-20" };
  const upcoming = { startDate: "2026-11-20", endDate: "2026-11-22" };
  const noDate = { startDate: null, endDate: null };
  const ended = { startDate: "2026-10-01", endDate: "2026-10-02" };
  assert.ok(matchesStatusTab(open, "open", NOW));
  assert.ok(!matchesStatusTab(open, "upcoming", NOW));
  assert.ok(matchesStatusTab(upcoming, "upcoming", NOW));
  assert.ok(!matchesStatusTab(upcoming, "open", NOW));
  assert.ok(matchesStatusTab(noDate, "open", NOW));
  assert.ok(!matchesStatusTab(ended, "open", NOW)); // 검증 ⚠️ 반영: "진행 중" 탭에 종료 행사가 섞이지 않음
  assert.ok(!matchesStatusTab(ended, "upcoming", NOW));
  assert.ok(matchesStatusTab(ended, null, NOW)); // 탭 안 씀 = 다 통과
});

test("PC 진행 탭이 걸리는 때: PC · 날짜 안 고름 · 검색어 없음 · 찜 아님 · 지난 행사 끔 (검증 ⚠️ 반영)", () => {
  const base = { mobile: false, dateValue: "all", keyword: "", category: "all", showPast: false };
  assert.ok(statusTabApplies(base));
  assert.ok(statusTabApplies({ ...base, category: "공연" })); // 분류는 같이 걸림
  assert.ok(!statusTabApplies({ ...base, mobile: true }));
  assert.ok(!statusTabApplies({ ...base, dateValue: "2026-11-14" }));
  assert.ok(!statusTabApplies({ ...base, keyword: "합창단" })); // 오픈예정 행사 이름을 검색해도 0건이 되지 않게
  assert.ok(!statusTabApplies({ ...base, category: "찜" }));
  assert.ok(!statusTabApplies({ ...base, showPast: true }));
});

test("지난 날짜 판별: 어제는 지난 날, 오늘은 아님 (11/13 P2: 지난 날도 고를 수 있음)", () => {
  assert.ok(isPastDay("2026-11-10", NOW));
  assert.ok(!isPastDay("2026-11-11", NOW));
});

test("하루 필터: 날짜 · 오늘만 하루, 주말 · 7일 · 전체는 하루 아님 (하루 이동 ◂ ▸ 은 하루일 때만)", () => {
  assert.strictEqual(singleDay("2026-11-08", NOW), "2026-11-08");
  assert.strictEqual(singleDay("today", NOW), "2026-11-11");
  assert.strictEqual(singleDay("weekend", NOW), null);
  assert.strictEqual(singleDay("week", NOW), null);
  assert.strictEqual(singleDay("all", NOW), null);
});

test("지난 날을 고르면 종료 행사도 보임, 그 밖엔 종료 행사 숨김 (지난 행사 보기 체크면 늘 보임)", () => {
  assert.strictEqual(pastSelectedDay("2026-11-08", NOW), "2026-11-08");
  assert.strictEqual(pastSelectedDay("2026-11-11", NOW), null); // 오늘
  assert.strictEqual(pastSelectedDay("today", NOW), null);
  assert.strictEqual(pastSelectedDay("weekend", NOW), null);
  assert.ok(!hidesEndedEvents("2026-11-08", false, NOW));
  assert.ok(hidesEndedEvents("2026-11-11", false, NOW));
  assert.ok(hidesEndedEvents("today", false, NOW));
  assert.ok(hidesEndedEvents("weekend", false, NOW));
  assert.ok(hidesEndedEvents("all", false, NOW));
  assert.ok(!hidesEndedEvents("all", true, NOW));
});

test("지난 날 고름 = 그날 열린 행사만 (matchRunDays 그대로: 종료 행사도 그날 열렸으면 보임)", () => {
  const ended = { startDate: "2026-11-01", endDate: "2026-11-09", scheduleStatus: "DATES", dates: ["2026-11-01", "2026-11-08", "2026-11-09"] }; // 11/9 종료, 진행일 있음
  const later = { startDate: "2026-11-10", endDate: "2026-11-20" };
  assert.strictEqual(matchRunDays(ended, filterDays("2026-11-08", NOW)), "yes");
  assert.strictEqual(matchRunDays(later, filterDays("2026-11-08", NOW)), "no");
  assert.deepStrictEqual(countRunDays([ended, later], ["2026-11-08", "2026-11-10"]), { "2026-11-08": 1, "2026-11-10": 1 });
});

test("하루 이동 칸 글자: 좁으면 11.8 (일), 지난 날 안내 문구", () => {
  assert.strictEqual(shortDayTitle("2026-11-08"), "11.8 (일)");
  assert.strictEqual(shortDayTitle("2026-12-25"), "12.25 (금)");
  assert.strictEqual(pastDayNote("2026-11-08"), "지난 행사 · 11월 8일 (일) 에 열린 행사만 지도와 목록에 표시");
});

test("달 이동 버튼: ‹ 10월 · 12월 › (해 넘김 포함)", () => {
  assert.strictEqual(monthStepLabel({ year: 2026, month: 11 }, -1), "‹ 10월");
  assert.strictEqual(monthStepLabel({ year: 2026, month: 11 }, 1), "12월 ›");
  assert.strictEqual(monthStepLabel({ year: 2026, month: 1 }, -1), "‹ 12월");
  assert.strictEqual(monthStepLabel({ year: 2026, month: 12 }, 1), "1월 ›");
});
