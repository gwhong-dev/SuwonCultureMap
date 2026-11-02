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
  // 올해가 아니면 연도까지 (지난 행사 보기에서 작년 행사가 올해처럼 보이지 않게)
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
  if (hasRunDates(event) && event.scheduleHolidayNote) lines.push("공휴일은 원문 확인");
  if (event.scheduleText) lines.push("일정 원문: " + event.scheduleText);
  return lines.join("\n");
}

// ===== "오늘 진행" · "이번 주말" 필터 (설계 7번) =====
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

function filterDays(filter, now) {
  if (filter === "today") return [koreaToday(now)];
  if (filter === "weekend") return upcomingWeekend(now);
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
// 시작일 · 종료일만으로 계산 (오늘은 한국 시간). 날짜 없는 행사는 null → 배지 없음
// "open" 운영중 · "upcoming" 오픈예정 · "ended" 종료
function eventStatus(event, now) {
  if (!event.startDate) return null;
  var today = koreaToday(now);
  var end = event.endDate || event.startDate;
  if (today < event.startDate) return "upcoming";
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
// 진행 날짜가 확정된 여러 날 행사만 날짜를 고름. 아니면 null (지금처럼 기간 전체 종일)
// 고를 수 있는 날: 오늘(한국 시간) 이후 진행일. 다 지났으면 전체. 기본값은 가장 가까운 날
function calendarDateChoices(event, now) {
  if (!hasRunDates(event) || event.startDate === event.endDate) return null;
  var today = koreaToday(now);
  var upcoming = event.dates.filter(function (date) {
    return date >= today;
  });
  var dates = upcoming.length > 0 ? upcoming : event.dates.slice();
  var thisYear = koreaYear(now === undefined ? Date.now() : now);
  return {
    dates: dates,
    selected: upcoming.length > 0 ? upcoming[0] : dates[dates.length - 1],
    labels: dates.map(function (date) {
      var day = WEEKDAY_NAMES[new Date(utcDay(date)).getUTCDay()];
      var text = date.slice(0, 4) === thisYear ? shortDate(date) : date;
      return text + " (" + day + ")";
    }),
  };
}

if (typeof module !== "undefined") {
  module.exports = {
    formatEventDates: formatEventDates,
    formatScheduleDetail: formatScheduleDetail,
    koreaToday: koreaToday,
    upcomingWeekend: upcomingWeekend,
    filterDays: filterDays,
    matchRunDays: matchRunDays,
    calendarDateChoices: calendarDateChoices,
    eventStatus: eventStatus,
    statusLabel: statusLabel,
    ddayText: ddayText,
  };
}
