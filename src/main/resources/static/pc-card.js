// PC 지도 하단 카드 규칙 (11/13 확정 시안 P1 — docs/design/2026-11-13-improvement-drafts.md)
//  - 카드 배지 · 기간 줄 · 장소 줄 글자, 카드 줄 위 '이 장소 행사 1 / 6' 띠, 마커 튀어오름 판단, 카드 줄 키보드, 길찾기 작은 창 자리
//  - 모바일 하단 카드는 그대로 (P3 담당) — 여기 함수는 PC 카드만 씀
// 시험: node --test src/test/js/ (CI 에서 실행)

var PC_STATUS_LABELS = { open: "진행 중", upcoming: "오픈 예정", ended: "종료" };

// 배지 2개: 상태(진행 중 · 오픈 예정 · 종료) + D-day(종료까지 D-37 · D-2). 날짜 없는 행사는 빈 배열
// statusOf · ddayOf = schedule-format.js eventStatus · ddayText (시험에서 바꿔 넣을 수 있게 인자로 받음)
function pcCardBadges(event, statusOf, ddayOf) {
  var status = statusOf(event);
  if (!status) return [];
  var badges = [{ kind: "status-badge " + status, text: PC_STATUS_LABELS[status] }];
  var dday = ddayOf(event);
  if (dday) badges.push({ kind: "dday-badge", text: dday });
  return badges;
}

function pcDotDate(isoDate, withYear) {
  var p = isoDate.split("-");
  return (withYear ? p[0] + "." : "") + p[1] + "." + p[2];
}

// 기간 줄: "08.01 – 12.19 · 주말". 올해가 아닌 날짜가 끼면 연도까지. summary = 요일 요약 · 총 N회 (없으면 null)
//  - "토 · 일" 은 "주말" 로 줄임 (카드 한 줄 폭)
function pcCardPeriod(event, thisYear, summary) {
  if (!event.startDate) return "기간 정보 없음";
  var end = event.endDate || event.startDate;
  var withYear = event.startDate.slice(0, 4) !== thisYear || end.slice(0, 4) !== thisYear;
  var range =
    end === event.startDate
      ? pcDotDate(event.startDate, withYear)
      : pcDotDate(event.startDate, withYear) + " – " + pcDotDate(end, withYear);
  if (!summary) return range;
  return range + " · " + (summary === "토 · 일" ? "주말" : summary);
}

// 장소 줄: "정조테마공연장 · 팔달구" (둘 다 없으면 null → 줄 숨김)
function pcCardPlace(place, district) {
  var parts = [];
  if (place) parts.push(place);
  if (district && (!place || place.indexOf(district) === -1)) parts.push(district);
  return parts.length ? parts.join(" · ") : null;
}

// 카드 줄 위 띠: 같은 장소 행사가 2개 이상일 때만 { count: "1 / 6", place }. 아니면 null (띠 숨김)
function placeBand(groupIndex, groupSize, place) {
  if (!(groupSize > 1)) return null;
  return { count: groupIndex + 1 + " / " + groupSize, place: place || "" };
}

// 같은 장소 다음 카드로 넘길 때만 마커가 튀어오름 (지도가 움직이지 않아 넘긴 걸 알리려고)
//  - 다른 장소로 넘기면 지도가 그 마커로 움직이니 생략, 움직임 줄이기 설정이면 생략
function shouldHopMarker(prevMarker, nextMarker, prevId, nextId, reduceMotion) {
  return !reduceMotion && !!prevMarker && prevMarker === nextMarker && prevId !== nextId;
}

// 카드 줄 키보드 (카드 줄에 포커스가 있을 때만). k = { key, onCard(포커스가 카드 자체), alt(조합키), handled }
//  - Enter = 상세, Space = 찜(페이지 스크롤 막음) → 카드 자체에 포커스가 있을 때만 (안쪽 버튼은 버튼 기본 동작)
//  - ← → 는 strip-nav.js stripKeyStep 이 맡음
function pcCardKeyAction(k) {
  if (k.handled || k.alt || !k.onCard) return null;
  if (k.key === "Enter") return "detail";
  if (k.key === " " || k.key === "Spacebar") return "favorite";
  return null;
}

// 길찾기 작은 창 자리: [길찾기] 버튼 위 가운데, 화면 밖으로 나가지 않게. 위 자리가 모자라면 버튼 아래
// button = { left, top, width, bottom }(화면 좌표), pop = { width, height }, view = { width, height }
var ROUTE_POP_GAP = 8;
function routePopPosition(button, pop, view) {
  var margin = 8;
  var left = button.left + button.width / 2 - pop.width / 2;
  left = Math.max(margin, Math.min(left, view.width - pop.width - margin));
  var top = button.top - pop.height - ROUTE_POP_GAP;
  if (top < margin) top = Math.min(button.bottom + ROUTE_POP_GAP, view.height - pop.height - margin);
  return { left: Math.round(left), top: Math.round(Math.max(margin, top)) };
}

if (typeof module !== "undefined") {
  module.exports = {
    PC_STATUS_LABELS: PC_STATUS_LABELS,
    pcCardBadges: pcCardBadges,
    pcCardPeriod: pcCardPeriod,
    pcCardPlace: pcCardPlace,
    placeBand: placeBand,
    shouldHopMarker: shouldHopMarker,
    pcCardKeyAction: pcCardKeyAction,
    routePopPosition: routePopPosition,
  };
}
