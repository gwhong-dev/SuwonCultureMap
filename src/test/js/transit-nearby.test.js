// 오시는 길(주변 탭 정류장 · 역) 그리기 시험: node --test src/test/js/
const test = require("node:test");
const assert = require("node:assert");
const { transitDistanceText, transitRouteChips, transitNearbyHtml, TRANSIT_ROUTE_LIMIT } =
  require("../../main/resources/static/transit-nearby.js");

test("거리: 1km 미만은 m, 이상은 소수 한 자리 km", () => {
  assert.strictEqual(transitDistanceText(230), "230m");
  assert.strictEqual(transitDistanceText(999), "999m");
  assert.strictEqual(transitDistanceText(1234), "1.2km");
  assert.strictEqual(transitDistanceText(1500), "1.5km");
  assert.strictEqual(transitDistanceText(undefined), "0m");
});

test("버스 번호 칩: 6개까지 보이고 나머지는 숨긴 칩 + '+N' 버튼", () => {
  const routes = ["2", "7", "11", "13", "15", "35", "60-1", "700-2"];
  const html = transitRouteChips(routes);
  assert.strictEqual(TRANSIT_ROUTE_LIMIT, 6);
  assert.strictEqual((html.match(/class="transit-chip">/g) || []).length, 6);
  assert.strictEqual((html.match(/class="transit-chip" hidden>/g) || []).length, 2);
  assert.match(html, /<button type="button" class="transit-more" aria-label="버스 2개 더 보기">\+2<\/button>/);
});

test("버스 번호가 6개 이하면 '+N' 없음, 비면 빈 글자", () => {
  assert.doesNotMatch(transitRouteChips(["2", "13"]), /transit-more/);
  assert.strictEqual(transitRouteChips([]), "");
  assert.strictEqual(transitRouteChips(["", " "]), "");
});

test("빈 응답 · 실패(null)면 빈 글자 → 칸을 숨김", () => {
  assert.strictEqual(transitNearbyHtml(null), "");
  assert.strictEqual(transitNearbyHtml({ busStops: [], subways: [] }), "");
});

test("정류장: 이름 · 도보 분 · 거리 · 버스 번호, 출처 한 줄", () => {
  const html = transitNearbyHtml({
    busStops: [{ name: "행궁동", distanceMeters: 120, walkMinutes: 2, routes: ["2", "13"] }],
    subways: [],
  });
  assert.match(html, /가까운 정류장/);
  assert.match(html, /<span class="transit-name">행궁동<\/span><span class="transit-dist">도보 2분 · 120m<\/span>/);
  assert.match(html, /<span class="transit-chip">13<\/span>/);
  assert.match(html, /경기도 버스정보/);
  assert.doesNotMatch(html, /가까운 역/);
});

test("역만 있으면 역 칸만, 호선을 이어 붙임 · 버스 출처는 안 씀", () => {
  const html = transitNearbyHtml({
    busStops: [],
    subways: [{ name: "수원역", lines: ["1호선", "수인분당선"], distanceMeters: 1234 }],
  });
  assert.match(html, /가까운 역/);
  assert.match(html, /수원역 <span class="transit-lines">1호선 · 수인분당선<\/span>/);
  assert.match(html, /직선 1\.2km/);
  assert.doesNotMatch(html, /가까운 정류장|경기도 버스정보/);
});

test("이름의 특수문자는 글자로 (HTML 로 해석되지 않음)", () => {
  const html = transitNearbyHtml({
    busStops: [{ name: '<img src=x onerror="a">', distanceMeters: 10, walkMinutes: 1, routes: ["<b>1</b>"] }],
    subways: [],
  });
  assert.doesNotMatch(html, /<img|<b>/);
  assert.match(html, /&lt;img src=x onerror=&quot;a&quot;&gt;/);
});
