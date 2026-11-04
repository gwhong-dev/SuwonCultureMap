// 모바일 홈(디자인 01) 칸별 행사 고르기 규칙 (계획 docs/design/mobile-home-search-plan.md 2번)
// 오늘 · 상태는 schedule-format.js 와 같은 기준(한국 시간). 시험: node --test src/test/js/ (CI 에서 실행)
var homeSchedule =
  typeof module !== "undefined"
    ? require("./schedule-format.js")
    : { eventStatus: eventStatus, koreaToday: koreaToday };

var HOME_SOON_MAX = 3; // 곧 종료 · 곧 시작 행 수

function homeAddDays(isoDate, days) {
  var parts = isoDate.split("-");
  var day = Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  return new Date(day + days * 86400000).toISOString().slice(0, 10);
}

// 이번 주 = 한국 시간 월 ~ 일 (일요일이면 지난 월요일부터)
function weekRange(now) {
  var today = homeSchedule.koreaToday(now);
  var weekday = new Date(today + "T00:00:00Z").getUTCDay(); // 0 = 일
  var monday = homeAddDays(today, weekday === 0 ? -6 : 1 - weekday);
  return { start: monday, end: homeAddDays(monday, 6) };
}

// "11월 2일 – 8일" · 달이 바뀌면 "10월 27일 – 11월 2일"
function weekLabel(range) {
  var s = range.start.split("-");
  var e = range.end.split("-");
  var end = s[1] === e[1] ? Number(e[2]) + "일" : Number(e[1]) + "월 " + Number(e[2]) + "일";
  return Number(s[1]) + "월 " + Number(s[2]) + "일 – " + end;
}

// 이번 주와 기간이 겹치고 종료 안 된 행사 (날짜 없는 행사는 뺌)
function thisWeekEvents(events, now) {
  var week = weekRange(now);
  return events.filter(function (event) {
    var status = homeSchedule.eventStatus(event, now);
    if (status !== "open" && status !== "upcoming") return false;
    return !event.startDate || event.startDate <= week.end; // 종료 안 됨 = 끝나는 날 ≥ 오늘 ≥ 월요일
  });
}

// 같은 날이면 제목 순 (새로고침마다 순서가 바뀌지 않게)
function byDateThenTitle(dateOf) {
  return function (a, b) {
    var diff = dateOf(a).localeCompare(dateOf(b));
    return diff !== 0 ? diff : (a.title || "").localeCompare(b.title || "");
  };
}

// 곧 종료: 운영중, 종료일 가까운 순 3개
function endingSoon(events, now) {
  return events
    .filter(function (event) {
      return homeSchedule.eventStatus(event, now) === "open";
    })
    .sort(
      byDateThenTitle(function (event) {
        return event.endDate || event.startDate;
      }),
    )
    .slice(0, HOME_SOON_MAX);
}

// 곧 시작: 오픈예정, 시작일 가까운 순 3개
function startingSoon(events, now) {
  return events
    .filter(function (event) {
      return homeSchedule.eventStatus(event, now) === "upcoming";
    })
    .sort(
      byDateThenTitle(function (event) {
        return event.startDate;
      }),
    )
    .slice(0, HOME_SOON_MAX);
}

var HOME_CAROUSEL_MAX = 5; // 포스터 캐러셀 카드 수

// 포스터 캐러셀: 이번 주 행사 중 사진 있는 것 → 운영중 먼저 → 종료일 가까운 순 → 최대 5
function carouselPicks(events, now) {
  var byEnd = byDateThenTitle(function (event) {
    return event.endDate || event.startDate;
  });
  return thisWeekEvents(events, now)
    .filter(function (event) {
      return Boolean(event.imageUrl && event.imageUrl.trim());
    })
    .sort(function (a, b) {
      var aOpen = homeSchedule.eventStatus(a, now) === "open" ? 0 : 1;
      var bOpen = homeSchedule.eventStatus(b, now) === "open" ? 0 : 1;
      return aOpen !== bOpen ? aOpen - bOpen : byEnd(a, b);
    })
    .slice(0, HOME_CAROUSEL_MAX);
}

var HOME_MONTH_MAX = 10; // 이달의 행사 카드 수 (끝에 "전체 보기" 카드 따로)

// 이번 달 = 한국 시간 1일 ~ 말일
function monthRange(now) {
  var today = homeSchedule.koreaToday(now);
  var parts = today.split("-");
  var lastDay = new Date(Date.UTC(Number(parts[0]), Number(parts[1]), 0)).getUTCDate(); // 다음 달 0일 = 이달 말일
  return { start: parts[0] + "-" + parts[1] + "-01", end: parts[0] + "-" + parts[1] + "-" + lastDay };
}

// "11월의 수원 행사"
function monthTitle(now) {
  return Number(monthRange(now).start.split("-")[1]) + "월의 수원 행사";
}

// 이달의 행사: 이번 달과 기간이 겹치고 종료 안 된 행사, 시작일 순 최대 10개 (날짜 없는 행사는 뺌)
function monthEvents(events, now) {
  var month = monthRange(now);
  return events
    .filter(function (event) {
      var status = homeSchedule.eventStatus(event, now);
      if (status !== "open" && status !== "upcoming") return false;
      return !event.startDate || event.startDate <= month.end; // 종료 안 됨 = 끝나는 날 ≥ 오늘 ≥ 1일
    })
    .sort(
      byDateThenTitle(function (event) {
        return event.startDate || event.endDate;
      }),
    )
    .slice(0, HOME_MONTH_MAX);
}

// 행사의 구: 서버가 좌표로 구한 값(D1) 먼저, 없으면(동기화 전 · 수원 밖) 원문 장소 글에서 찾음
var HOME_DISTRICT_PATTERN = /(장안|권선|팔달|영통)구/;
function eventDistrict(event) {
  if (!event) return null;
  if (event.district) return event.district;
  var m = HOME_DISTRICT_PATTERN.exec(event.info || "");
  return m ? m[0] : null;
}

// 구 필터 (null = 전체). 구를 모르는 행사는 구를 고르면 빠짐
function matchesDistrict(event, district) {
  return !district || eventDistrict(event) === district;
}

if (typeof module !== "undefined") {
  module.exports = {
    eventDistrict: eventDistrict,
    matchesDistrict: matchesDistrict,
    carouselPicks: carouselPicks,
    weekRange: weekRange,
    weekLabel: weekLabel,
    thisWeekEvents: thisWeekEvents,
    endingSoon: endingSoon,
    startingSoon: startingSoon,
    monthRange: monthRange,
    monthTitle: monthTitle,
    monthEvents: monthEvents,
  };
}
