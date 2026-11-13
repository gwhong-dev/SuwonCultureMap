// 날짜 고르기 규칙 (11/11 사용자 확정 시안 CalClosed · CalOpen · PcCard)
// 모바일 = 목록 탭 위 가로 날짜 줄(오늘부터) + "달력 펼치기"(한 달 달력), PC = 오른쪽 목록 위 탭 [진행 중 · 진행 예정 · 달력 ▾]
// 고른 날짜는 #dateFilter 값("2026-11-14")으로 → 그날 진행 판정은 schedule-format.js matchRunDays 그대로
// 시험: node --test src/test/js/ (CI 에서 실행)
var calSchedule =
  typeof module !== "undefined"
    ? require("./schedule-format.js")
    : { koreaToday: koreaToday, addDays: addDays, filterDays: filterDays, matchRunDays: matchRunDays, eventStatus: eventStatus };

// 화면 언어 (i18n.js): 요일 · "오늘" · 날짜 글자 (node 시험 = 한국어)
var calI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;
var CAL_WEEKDAYS = calI18n.list("date.weekdays");
var STRIP_DAYS = 14; // 날짜 줄 칸 수 (오늘 포함 2주, 가로로 넘김)

function isDayValue(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || "");
}

// 0 = 일 … 6 = 토 (날짜 글자만으로 계산 → 기기 시간대와 상관없음)
function weekdayOf(isoDate) {
  return new Date(Date.UTC(+isoDate.slice(0, 4), +isoDate.slice(5, 7) - 1, +isoDate.slice(8, 10))).getUTCDay();
}

// 날짜 줄: 오늘부터 count 일. 첫 칸 요일 자리는 "오늘"
function stripDays(today, count) {
  var days = [];
  for (var i = 0; i < count; i++) {
    var date = calSchedule.addDays(today, i);
    var weekday = weekdayOf(date);
    days.push({ date: date, label: i === 0 ? calI18n.t("cal.today") : CAL_WEEKDAYS[weekday], num: +date.slice(8, 10), weekday: weekday });
  }
  return days;
}

// 한 달 달력 칸: 1일 앞은 빈 칸(null), 그다음 그 달 날짜. month 는 1~12
function monthCells(year, month) {
  var first = year + "-" + pad2(month) + "-01";
  var cells = [];
  for (var i = weekdayOf(first); i > 0; i--) cells.push(null);
  for (var day = first; day.slice(0, 7) === first.slice(0, 7); day = calSchedule.addDays(day, 1)) cells.push(day);
  return cells;
}

function pad2(n) {
  return (n < 10 ? "0" : "") + n;
}

// { year, month } 를 delta 달만큼 옮김 (12월 → 다음 해 1월)
function shiftMonth(ym, delta) {
  var index = ym.year * 12 + (ym.month - 1) + delta;
  return { year: Math.floor(index / 12), month: (index % 12) + 1 };
}

function monthOf(isoDate) {
  return { year: +isoDate.slice(0, 4), month: +isoDate.slice(5, 7) };
}

// 날짜마다 그날 진행하는 행사 수. "maybe"(기간 안 · 날짜 모름)도 목록에 보이므로 셈 (목록 결과 수와 같게)
function countRunDays(events, days) {
  var counts = {};
  days.forEach(function (day) {
    counts[day] = 0;
  });
  events.forEach(function (event) {
    days.forEach(function (day) {
      if (calSchedule.matchRunDays(event, [day]) !== "no") counts[day]++;
    });
  });
  return counts;
}

// 달력 칸 아래 점 개수 (0~3): 1~2개 = 1, 3~5개 = 2, 6개 이상 = 3
function dotLevel(count) {
  if (!count) return 0;
  if (count <= 2) return 1;
  if (count <= 5) return 2;
  return 3;
}

// 날짜 줄 · 달력에서 칠할 날짜들 (홈 "이번 주말" · "+7일"로 켠 필터도 여러 칸으로 보임). 필터 없음 = []
function highlightedDays(filterValue, now) {
  return calSchedule.filterDays(filterValue, now) || [];
}

// 이 날짜를 누르면 바뀔 #dateFilter 값: 혼자 골라진 날을 다시 누르면 해제("all"), 아니면 그날 하나
function nextDayFilter(filterValue, day, now) {
  var current = highlightedDays(filterValue, now);
  return current.length === 1 && current[0] === day ? "all" : day;
}

// "11월 14일 (토)" · "Sat, Nov 14" (i18n.js dayTitle)
function dayTitle(isoDate) {
  return calI18n.dayTitle(isoDate);
}

// 목록 위 제목 · 지도 칩 · PC 달력 탭 글자. 필터 없음 = null
function dateFilterTitle(filterValue, now) {
  if (isDayValue(filterValue)) {
    return filterValue === calSchedule.koreaToday(now) ? calI18n.t("cal.todayTitle", { day: dayTitle(filterValue) }) : dayTitle(filterValue);
  }
  if (filterValue === "today") return calI18n.t("cal.todayTitle", { day: dayTitle(calSchedule.koreaToday(now)) });
  if (filterValue === "weekend") return calI18n.t("filter.weekend");
  if (filterValue === "week") return calI18n.t("filter.week");
  return null;
}

// PC 목록 위 탭: "open" 진행 중 = 운영중 · 날짜 없음 (종료는 안 셈), "upcoming" 진행 예정 = 오픈예정
// 두 탭을 합치면 날짜 필터 없는 기본 목록(지난 행사 빼고)과 같음. null = 탭 안 씀
function matchesStatusTab(event, tab, now) {
  if (!tab) return true;
  var status = calSchedule.eventStatus(event, now);
  return tab === "upcoming" ? status === "upcoming" : status !== "upcoming" && status !== "ended";
}

// PC 진행 탭을 걸지 않는 경우: 모바일 · 날짜를 고름 · 검색어 · '찜' 분류
// (검색 · 찜은 "그 행사를 찾는" 목록이라 탭 때문에 0건이 되지 않게 전부 보여 줌)
function statusTabApplies(state) {
  return !state.mobile && state.dateValue === "all" && !state.keyword && state.category !== "찜";
}

// 오늘 전 날짜 (11/13 P2: 지난 날도 고를 수 있음 — 달력 칸은 회색 바탕, 고르면 그날 열린 행사만)
function isPastDay(isoDate, now) {
  return isoDate < calSchedule.koreaToday(now);
}

// 날짜 필터가 "하루"면 그 날짜("today" = 오늘), 아니면(전체 · 주말 · 7일) null → 하루 이동 ◂ 전날 · 다음날 ▸ 은 하루일 때만
function singleDay(filterValue, now) {
  if (isDayValue(filterValue)) return filterValue;
  if (filterValue === "today") return calSchedule.koreaToday(now);
  return null;
}

// 고른 날짜가 오늘 전이면 그 날짜 (지난 행사 안내 · 종료 행사도 보여 줄 때), 아니면 null
function pastSelectedDay(filterValue, now) {
  var day = singleDay(filterValue, now);
  return day && isPastDay(day, now) ? day : null;
}

// 종료 행사를 목록 · 지도에서 뺄까: 기본은 뺌, 지난 날짜를 골랐으면(그날 열린 행사) 안 뺌
function hidesEndedEvents(filterValue, now) {
  return !pastSelectedDay(filterValue, now);
}

// 하루 이동 칸 글자: 넓으면 "11월 8일 (일)", 좁으면 "11.8 (일)" (375px 에서도 한 줄 — 11/13 사용자 지적)
function shortDayTitle(isoDate) {
  return +isoDate.slice(5, 7) + "." + +isoDate.slice(8, 10) + " (" + CAL_WEEKDAYS[weekdayOf(isoDate)] + ")";
}

// 지난 날을 골랐을 때 아래 안내
function pastDayNote(isoDate) {
  return calI18n.t("cal.pastNote", { day: dayTitle(isoDate) });
}

// 달 이동 버튼 글자: 앞 = "‹ 10월", 뒤 = "12월 ›"
function monthStepLabel(ym, delta) {
  var month = shiftMonth(ym, delta).month;
  return delta < 0 ? "‹ " + calI18n.month(month) : calI18n.month(month) + " ›";
}

if (typeof module !== "undefined") {
  module.exports = {
    STRIP_DAYS: STRIP_DAYS,
    isDayValue: isDayValue,
    weekdayOf: weekdayOf,
    stripDays: stripDays,
    monthCells: monthCells,
    shiftMonth: shiftMonth,
    monthOf: monthOf,
    countRunDays: countRunDays,
    dotLevel: dotLevel,
    highlightedDays: highlightedDays,
    nextDayFilter: nextDayFilter,
    dayTitle: dayTitle,
    dateFilterTitle: dateFilterTitle,
    matchesStatusTab: matchesStatusTab,
    statusTabApplies: statusTabApplies,
    isPastDay: isPastDay,
    singleDay: singleDay,
    pastSelectedDay: pastSelectedDay,
    hidesEndedEvents: hidesEndedEvents,
    shortDayTitle: shortDayTitle,
    pastDayNote: pastDayNote,
    monthStepLabel: monthStepLabel,
  };
}
