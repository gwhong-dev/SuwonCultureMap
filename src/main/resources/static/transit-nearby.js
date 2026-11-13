// 오시는 길 — 상세 "주변" 탭의 가까운 정류장 · 버스 번호 · 역 (설계 docs/transit-directions.md 3번)
// 서버 /api/events/{id}/transit 응답 { busStops: [{ name, distanceMeters, walkMinutes, routes }], subways: [{ name, lines, distanceMeters }] }
// 을 HTML 글자로만 만듦 (연결 · 불러오기는 js/index.js loadDetailTransit). 빈 응답이면 "" → 칸을 숨김
// 시험: node --test src/test/js/ (CI 에서 실행)

// 화면 언어 (i18n.js, node 시험 = 한국어)
var transitI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;
var TRANSIT_ROUTE_LIMIT = 6; // 버스 번호는 6개까지 보이고 나머지는 "+N" 을 누르면 펼침

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
          var lines = (w.lines || []).join(" · ");
          return (
            '<li class="transit-item"><div class="transit-head"><span class="transit-name">' +
            transitEscape(w.name) +
            (lines ? ' <span class="transit-lines">' + transitEscape(lines) + "</span>" : "") +
            '</span><span class="transit-dist">' +
            transitEscape(transitI18n.t("transit.straight")) +
            " " +
            transitDistanceText(w.distanceMeters) +
            "</span></div></li>"
          );
        })
        .join("") +
      "</ul></div>";
  }
  var source = stops.length ? " · " + transitI18n.t("transit.source") : "";
  html += '<p class="transit-note">' + transitEscape(transitI18n.t("transit.note") + source) + "</p>";
  return html;
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
    TRANSIT_ROUTE_LIMIT: TRANSIT_ROUTE_LIMIT,
  };
}
