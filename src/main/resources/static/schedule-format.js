// 실제 진행 날짜 표시 규칙 (설계 docs/event-schedule.md 7번)
// 시험: node --test src/test/js/schedule-format.test.js (CI 에서 실행)
var SCHEDULE_LIST_MAX = 5; // 이보다 많으면 카드에 날짜를 다 쓰지 않고 요약
var WEEKDAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];

function formatDateRange(event) {
  if (!event.startDate) return "기간 정보 없음";
  return (
    event.startDate +
    (event.endDate && event.endDate !== event.startDate
      ? " ~ " + event.endDate
      : "")
  );
}

// 날짜가 기간 안에 있을 때만 믿음. 어긋나면 기간만 표시 (본문을 못 받은 동기화에서 기간만 바뀌면 생길 수 있음)
function hasRunDates(event) {
  if (event.scheduleStatus !== "DATES" || !event.dates || event.dates.length === 0) return false;
  if (!event.startDate || !event.endDate) return false;
  return event.dates.every(function (date) {
    return date >= event.startDate && date <= event.endDate;
  });
}

function shortDate(isoDate) {
  var parts = isoDate.split("-");
  return Number(parts[1]) + "/" + Number(parts[2]);
}

function utcDay(isoDate) {
  var parts = isoDate.split("-");
  return Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
}

function koreaYear(now) {
  return String(new Date(now + 9 * 3600000).getUTCFullYear());
}

// 기간 안의 그 요일이 빠짐없이 있을 때만 요약 (총 2회뿐인 토요일 행사를 "토요일마다"로 안내하지 않게)
function weekdaySummary(event) {
  var runDays = new Set(event.dates.map(utcDay));
  var weekdays = new Set();
  runDays.forEach(function (day) {
    weekdays.add(new Date(day).getUTCDay());
  });
  var expected = 0;
  for (var day = utcDay(event.startDate); day <= utcDay(event.endDate); day += 86400000) {
    if (!weekdays.has(new Date(day).getUTCDay())) continue;
    if (!runDays.has(day)) return null;
    expected++;
  }
  if (expected !== runDays.size) return null;
  if (weekdays.size === 7) return "매일";
  // 월요일부터 일요일 순서로 표시. 4일 이상 이어지면 "월~토" 로 줄임 (모바일 카드 폭이 좁음)
  var order = [1, 2, 3, 4, 5, 6, 0];
  var kept = order.filter(function (d) {
    return weekdays.has(d);
  });
  if (kept.length === 1) return WEEKDAY_NAMES[kept[0]] + "요일만";
  var consecutive = order.indexOf(kept[kept.length - 1]) - order.indexOf(kept[0]) === kept.length - 1;
  if (kept.length >= 4 && consecutive) {
    return WEEKDAY_NAMES[kept[0]] + "~" + WEEKDAY_NAMES[kept[kept.length - 1]];
  }
  return kept
    .map(function (d) {
      return WEEKDAY_NAMES[d];
    })
    .join(" · ");
}

// 카드 · 상세 첫 줄. 모름은 지금처럼 기간만 ("매일 진행"이라는 뜻이 아님)
function formatEventDates(event, now) {
  if (!hasRunDates(event) || event.startDate === event.endDate) return formatDateRange(event);
  var dates = event.dates;
  var thisYear = koreaYear(now === undefined ? Date.now() : now);
  // 올해가 아니면 연도까지 (지난 날짜를 골랐을 때 작년 행사가 올해처럼 보이지 않게)
  var sameYear = event.startDate.slice(0, 4) === thisYear && event.endDate.slice(0, 4) === thisYear;
  var range = sameYear ? shortDate(event.startDate) + " ~ " + shortDate(event.endDate) : formatDateRange(event);
  var text;
  if (dates.length <= SCHEDULE_LIST_MAX) {
    text = range + " · 총 " + dates.length + "회 · " + dates.map(shortDate).join(", ");
  } else {
    text = range + " · " + (weekdaySummary(event) || "총 " + dates.length + "회");
  }
  return event.scheduleHolidayNote ? text + " · 공휴일 확인" : text;
}

// 상세 팝업: 날짜가 많으면 달별 전체 목록 + 공휴일 안내 + 일정 원문
function formatScheduleDetail(event) {
  var lines = [];
  if (hasRunDates(event) && event.dates.length > SCHEDULE_LIST_MAX) {
    var crossYear = event.startDate.slice(0, 4) !== event.endDate.slice(0, 4);
    var byMonth = new Map();
    event.dates.forEach(function (date) {
      var parts = date.split("-");
      var month = (crossYear ? parts[0] + "년 " : "") + Number(parts[1]) + "월";
      if (!byMonth.has(month)) byMonth.set(month, []);
      byMonth.get(month).push(Number(parts[2]));
    });
    lines.push("진행일 " + event.dates.length + "회");
    byMonth.forEach(function (days, month) {
      lines.push(month + ": " + days.join(", "));
    });
  }
  // 합쳐 숨긴 글에서 확인된 회차 (설계 8번). 대표 날짜를 모를 때만 오고, "이날은 한다"는 뜻이라 필터 · 캘린더는 안 씀
  var confirmed = !hasRunDates(event) && event.confirmedDates ? event.confirmedDates : [];
  if (confirmed.length > 0) {
    var oneYear = confirmed.every(function (date) {
      return date.slice(0, 4) === confirmed[0].slice(0, 4);
    });
    lines.push("확인된 회차: " + confirmed.map(oneYear ? shortDate : String).join(", "));
  }
  if (hasRunDates(event) && event.scheduleHolidayNote) lines.push("공휴일은 원문 확인");
  if (event.scheduleText) lines.push("일정 원문: " + event.scheduleText);
  return lines.join("\n");
}

// 모바일 상세 표 "진행" 줄. 일정 원문 한 줄뿐이고 그 값이 "시간" 줄과 같으면 같은 글이 두 번 보여 뺌
function scheduleRowText(event, timeText) {
  var text = formatScheduleDetail(event);
  var onlyRaw = event.scheduleText && text === "일정 원문: " + event.scheduleText;
  var squash = function (value) {
    return String(value || "").replace(/\s+/g, "");
  };
  if (onlyRaw && timeText && squash(event.scheduleText) === squash(timeText)) return "";
  return text;
}

// ===== "진행 중"(오늘 진행) · "이번 주말" · "7일 안" 필터 (설계 7번) =====
// 오늘은 한국 시간 기준 (toISOString 은 UTC 라 오전 9시 전엔 전날이 됨)
function koreaToday(now) {
  return new Date((now === undefined ? Date.now() : now) + 9 * 3600000).toISOString().slice(0, 10);
}

function addDays(isoDate, days) {
  return new Date(utcDay(isoDate) + days * 86400000).toISOString().slice(0, 10);
}

// 오늘 이후 가장 가까운 토 · 일. 일요일이면 남은 주말은 오늘 하루
function upcomingWeekend(now) {
  var today = koreaToday(now);
  var weekday = new Date(utcDay(today)).getUTCDay();
  if (weekday === 0) return [today];
  var saturday = addDays(today, 6 - weekday);
  return [saturday, addDays(saturday, 1)];
}

// 오늘 포함 7일 (지도 칩 "7일 안" · 홈 곧 종료 | 곧 시작 "7일 안"과 같은 기간)
function nextSevenDays(now) {
  var today = koreaToday(now);
  var days = [];
  for (var i = 0; i < 7; i++) days.push(addDays(today, i));
  return days;
}

// filter: "today" · "weekend" · "week" 또는 날짜 하나 "2026-11-14" (목록 위 날짜 줄 · 달력, calendar-strip.js)
function filterDays(filter, now) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(filter || "")) return [filter];
  if (filter === "today") return [koreaToday(now)];
  if (filter === "weekend") return upcomingWeekend(now);
  if (filter === "week") return nextSevenDays(now);
  return null;
}

// 고른 날에 진행하나: "yes" 확실 · "maybe" 기간 안이지만 날짜 모름 · "no"
// 모름 행사는 숨기지 않고 "기간 중 · 일정 확인" 으로 보여 줌 (행사가 사라지지 않게)
function matchRunDays(event, days) {
  if (!event.startDate) return "no";
  var end = event.endDate || event.startDate;
  if (hasRunDates(event)) {
    return days.some(function (day) {
      return event.dates.indexOf(day) !== -1;
    })
      ? "yes"
      : "no";
  }
  var inPeriod = days.some(function (day) {
    return day >= event.startDate && day <= end;
  });
  if (!inPeriod) return "no";
  // 하루짜리 행사는 기간이 곧 진행일
  return event.startDate === end ? "yes" : "maybe";
}

var MAYBE_RUN_NOTE = "기간 중 · 일정 확인";

// ===== 상태 배지 · D-day (디자인 docs/design/mobile-design-v1.md 2번) =====
// 시작일 · 종료일만으로 계산 (오늘은 한국 시간). 둘 다 없는 행사는 null → 배지 없음
// "open" 운영중 · "upcoming" 오픈예정 · "ended" 종료
// 한쪽만 있으면 그 날 하루 행사로 봄. 단 시작일만 없으면 이미 시작한 것으로 보고 종료일까지 운영중
function eventStatus(event, now) {
  var start = event.startDate;
  var end = event.endDate || start;
  if (!end) return null;
  var today = koreaToday(now);
  if (start && today < start) return "upcoming";
  if (today > end) return "ended";
  return "open";
}

var STATUS_LABELS = { open: "운영중", upcoming: "오픈예정", ended: "종료" };

function statusLabel(status) {
  return STATUS_LABELS[status] || "";
}

// 오늘(한국 시간)부터 그날까지 남은 날 수
function daysUntil(isoDate, now) {
  return Math.round((utcDay(isoDate) - utcDay(koreaToday(now))) / 86400000);
}

// 운영중 = "종료까지 D-N" (마지막 날은 "오늘 종료"), 오픈예정 = "D-N" (시작까지), 종료 · 날짜 없음 = null
function ddayText(event, now) {
  var status = eventStatus(event, now);
  if (status === "open") {
    var left = daysUntil(event.endDate || event.startDate, now);
    return left === 0 ? "오늘 종료" : "종료까지 D-" + left;
  }
  if (status === "upcoming") return "D-" + daysUntil(event.startDate, now);
  return null;
}

// ===== 캘린더 저장 날짜 고르기 (설계 7번) =====
// ① 진행 날짜가 확정된 여러 날 행사: 진행일 중 고름
//    고를 수 있는 날: 오늘(한국 시간) 이후 진행일. 다 지났으면 전체. 기본값은 가장 가까운 날
// ② 기간이 30일을 넘는 행사(상설 등, 11/11 사용자 결정): 몇 달짜리 종일 막대 대신 기간 안 하루
//    고를 수 있는 날: 오늘(기간 전이면 시작일, 끝났으면 시작일)부터 최대 90일. 기본값은 그 첫날
// 둘 다 아니면 null (지금처럼 기간 전체 종일)
var LONG_EVENT_DAYS = 30; // 서버 EventCalendarService.LONG_EVENT_DAYS 와 같은 값
var LONG_EVENT_CHOICES = 90;

function calendarDateChoices(event, now) {
  var dates;
  var selected;
  if (hasRunDates(event) && event.startDate !== event.endDate) {
    var today = koreaToday(now);
    var upcoming = event.dates.filter(function (date) {
      return date >= today;
    });
    dates = upcoming.length > 0 ? upcoming : event.dates.slice();
    selected = upcoming.length > 0 ? upcoming[0] : dates[dates.length - 1];
  } else if (isLongEvent(event)) {
    dates = longEventDays(event, now);
    selected = dates[0];
  } else {
    return null;
  }
  var thisYear = koreaYear(now === undefined ? Date.now() : now);
  return {
    dates: dates,
    selected: selected,
    labels: dates.map(function (date) {
      var day = WEEKDAY_NAMES[new Date(utcDay(date)).getUTCDay()];
      var text = date.slice(0, 4) === thisYear ? shortDate(date) : date;
      return text + " (" + day + ")";
    }),
  };
}

// 시작 · 종료일 포함 일수가 30일을 넘으면 긴 행사 (30일까지는 지금처럼 기간 전체)
function isLongEvent(event) {
  if (!event.startDate || !event.endDate || event.endDate < event.startDate) return false;
  return daysBetween(event.startDate, event.endDate) + 1 > LONG_EVENT_DAYS;
}

function longEventDays(event, now) {
  var today = koreaToday(now);
  var first = today >= event.startDate && today <= event.endDate ? today : event.startDate;
  var days = [];
  for (var day = first; day <= event.endDate && days.length < LONG_EVENT_CHOICES; day = addDays(day, 1)) {
    days.push(day);
  }
  return days;
}

function daysBetween(fromIso, toIso) {
  return Math.round((utcDay(toIso) - utcDay(fromIso)) / 86400000);
}

if (typeof module !== "undefined") {
  module.exports = {
    formatEventDates: formatEventDates,
    formatScheduleDetail: formatScheduleDetail,
    scheduleRowText: scheduleRowText,
    koreaToday: koreaToday,
    addDays: addDays,
    upcomingWeekend: upcomingWeekend,
    filterDays: filterDays,
    matchRunDays: matchRunDays,
    calendarDateChoices: calendarDateChoices,
    isLongEvent: isLongEvent,
    eventStatus: eventStatus,
    statusLabel: statusLabel,
    ddayText: ddayText,
    daysUntil: daysUntil,
  };
}
