// 오시는 길 — 상세 "주변" 탭의 가까운 정류장 · 버스 번호 · 역 (설계 docs/transit-directions.md 3번)
// 서버 /api/events/{id}/transit 응답 { busStops: [{ name, distanceMeters, walkMinutes, routes }], subways: [{ name, lines, distanceMeters }], subwayChecked }
// 을 HTML 글자로만 만듦 (연결 · 불러오기는 js/index.js loadDetailTransit · loadPcTransit). 빈 응답이면 "" → 칸을 숨김
// - 모바일 주변 탭 = transitNearbyHtml, PC 상세 칸 "오시는 길" = transitPcHtml (확정 시안 docs/design/2026-11-13-transit-next.md)
// - 역 없음 줄: subwayChecked(역 검색이 실제로 끝남) + 빈 목록일 때만. 실패(false)는 역 칸을 숨김
// 시험: node --test src/test/js/ (CI 에서 실행)

// 화면 언어 (i18n.js, node 시험 = 한국어)
var transitI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;
var TRANSIT_ROUTE_LIMIT = 6; // 버스 번호는 6개까지 보이고 나머지는 "+N" 을 누르면 펼침
var TRANSIT_PC_ROUTE_LIMIT = 4; // PC 정류장 칸(2칸 격자)은 좁아서 4개 + "+N"
var TRANSIT_SUBWAY_RADIUS_KM = 1.5; // 역 찾는 반경 = 서버 KakaoSubwayClient.RADIUS_M (1500)

function transitEscape(text) {
  return String(text == null ? "" : text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// 230 → "230m", 1234 → "1.2km"
function transitDistanceText(meters) {
  var m = Math.max(0, Math.round(Number(meters) || 0));
  if (m < 1000) return m + "m";
  return (Math.round(m / 100) / 10).toFixed(1) + "km";
}

// 버스 번호 칩: 앞 limit 개는 보이고, 나머지는 숨긴 칩 + "+N" 버튼
function transitRouteChips(routes, limit) {
  var list = (routes || []).filter(function (r) {
    return r != null && String(r).trim() !== "";
  });
  if (!list.length) return "";
  var max = limit == null ? TRANSIT_ROUTE_LIMIT : limit;
  var html = list
    .map(function (r, i) {
      return '<span class="transit-chip"' + (i >= max ? " hidden" : "") + ">" + transitEscape(r) + "</span>";
    })
    .join("");
  if (list.length > max) {
    var rest = list.length - max;
    html += '<button type="button" class="transit-more" aria-label="' + transitEscape(transitI18n.t("transit.moreBus", { n: rest })) + '">+' + rest + "</button>";
  }
  return '<div class="transit-routes">' + html + "</div>";
}

// 역 이름 + 호선(보조색)
function transitStationNameHtml(w) {
  var lines = (w.lines || []).join(" · ");
  return transitEscape(w.name) + (lines ? ' <span class="transit-lines">' + transitEscape(lines) + "</span>" : "");
}

// "근처 지하철역 없음 · 1.5km 안" 줄 (모바일 · PC 공통, 점선 상자)
function transitNoStationHtml() {
  return (
    '<div class="transit-none"><span>' + transitEscape(transitI18n.t("transit.noStation")) +
    "</span><span>" + transitEscape(transitI18n.t("transit.withinKm", { n: TRANSIT_SUBWAY_RADIUS_KM })) + "</span></div>"
  );
}

// 역이 없을 때 "없음" 줄을 보일까: 역 검색이 끝났고(subwayChecked) 정말 비었을 때만
function transitShowNoStation(data) {
  return !!(data && data.subwayChecked === true && !((data.subways || []).length));
}

// 출처 한 줄: 버스가 있을 때만 경기도 출처를 붙임
function transitNoteHtml(hasStops) {
  var source = hasStops ? " · " + transitI18n.t("transit.source") : "";
  return '<p class="transit-note">' + transitEscape(transitI18n.t("transit.note") + source) + "</p>";
}

function transitNearbyHtml(data) {
  var stops = (data && data.busStops) || [];
  var subways = (data && data.subways) || [];
  if (!stops.length && !subways.length) return "";
  var html = "";
  if (stops.length) {
    html +=
      '<div class="transit-block"><h4 class="transit-title">' + transitEscape(transitI18n.t("transit.stops")) + '</h4><ul class="transit-list">' +
      stops
        .map(function (s) {
          return (
            '<li class="transit-item"><div class="transit-head"><span class="transit-name">' +
            transitEscape(s.name) +
            '</span><span class="transit-dist">' +
            transitEscape(transitI18n.t("transit.walk", { n: s.walkMinutes })) +
            " · " +
            transitDistanceText(s.distanceMeters) +
            "</span></div>" +
            transitRouteChips(s.routes) +
            "</li>"
          );
        })
        .join("") +
      "</ul></div>";
  }
  if (subways.length) {
    html +=
      '<div class="transit-block"><h4 class="transit-title">' + transitEscape(transitI18n.t("transit.stations")) + '</h4><ul class="transit-list">' +
      subways
        .map(function (w) {
          return (
            '<li class="transit-item"><div class="transit-head"><span class="transit-name">' +
            transitStationNameHtml(w) +
            '</span><span class="transit-dist">' +
            transitEscape(transitI18n.t("transit.straight")) +
            " " +
            transitDistanceText(w.distanceMeters) +
            "</span></div></li>"
          );
        })
        .join("") +
      "</ul></div>";
  } else if (transitShowNoStation(data)) {
    html += '<div class="transit-block"><h4 class="transit-title">' + transitEscape(transitI18n.t("transit.stations")) + "</h4>" + transitNoStationHtml() + "</div>";
  }
  return html + transitNoteHtml(stops.length > 0);
}

// PC 상세 칸 "오시는 길" (PC A): 제목 → 정류장 2칸 격자(칩 4개 + "+N") → 역 한 줄씩(없으면 "없음" 줄) → 출처
function transitPcHtml(data) {
  var stops = (data && data.busStops) || [];
  var subways = (data && data.subways) || [];
  if (!stops.length && !subways.length) return "";
  var html = '<h3 class="pcd-transit-title">' + transitEscape(transitI18n.t("transit.pcTitle")) + "</h3>";
  if (stops.length) {
    html +=
      '<ul class="pcd-stops">' +
      stops
        .map(function (s) {
          return (
            '<li class="pcd-stop"><span class="pcd-stop-label">' + transitEscape(transitI18n.t("transit.stopLabel")) +
            '</span><span class="pcd-stop-name">' + transitEscape(s.name) +
            '</span><span class="pcd-stop-dist">' +
            transitEscape(transitI18n.t("transit.walk", { n: s.walkMinutes })) + " · " + transitDistanceText(s.distanceMeters) +
            "</span>" + transitRouteChips(s.routes, TRANSIT_PC_ROUTE_LIMIT) + "</li>"
          );
        })
        .join("") +
      "</ul>";
  }
  if (subways.length) {
    html += subways
      .map(function (w) {
        return (
          '<div class="pcd-station"><span class="transit-name">' + transitStationNameHtml(w) +
          '</span><span class="transit-dist">' + transitEscape(transitI18n.t("transit.straight")) + " " +
          transitDistanceText(w.distanceMeters) + "</span></div>"
        );
      })
      .join("");
  } else if (transitShowNoStation(data)) {
    html += transitNoStationHtml();
  }
  return html + transitNoteHtml(stops.length > 0);
}

// "+N" 을 누르면 그 정류장의 숨긴 버스 번호를 모두 보이고 버튼은 없앰
function expandTransitRoutes(button) {
  var row = button.parentNode;
  Array.prototype.forEach.call(row.querySelectorAll(".transit-chip[hidden]"), function (chip) {
    chip.hidden = false;
  });
  row.removeChild(button);
}

if (typeof module !== "undefined") {
  module.exports = {
    transitDistanceText: transitDistanceText,
    transitRouteChips: transitRouteChips,
    transitNearbyHtml: transitNearbyHtml,
    transitPcHtml: transitPcHtml,
    TRANSIT_ROUTE_LIMIT: TRANSIT_ROUTE_LIMIT,
    TRANSIT_PC_ROUTE_LIMIT: TRANSIT_PC_ROUTE_LIMIT,
  };
}
