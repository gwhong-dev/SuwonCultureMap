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

if (typeof module !== "undefined") {
  module.exports = { formatEventDates: formatEventDates, formatScheduleDetail: formatScheduleDetail };
}
