// 오시는 길(주변 탭 정류장 · 역) 그리기 시험: node --test src/test/js/
const test = require("node:test");
const assert = require("node:assert");
const { transitDistanceText, transitRouteChips, transitNearbyHtml, transitPcHtml, TRANSIT_ROUTE_LIMIT, TRANSIT_PC_ROUTE_LIMIT } =
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

// ----- 오시는 길 다음 단계 (확정 시안 docs/design/2026-11-13-transit-next.md) -----

const STOP = { name: "행궁동", distanceMeters: 140, walkMinutes: 2, routes: ["2", "7", "7-2", "11", "13", "16", "35"] };

test("역 없음 줄: 역 검색이 끝났고(subwayChecked) 비었을 때만 — 모바일", () => {
  const html = transitNearbyHtml({ busStops: [STOP], subways: [], subwayChecked: true });
  assert.match(html, /가까운 역/);
  assert.match(html, /<div class="transit-none"><span>근처 지하철역 없음<\/span><span>1\.5km 안<\/span><\/div>/);
});

test("역 검색 실패(subwayChecked 없음 · false)면 역 칸을 숨김 — '없음' 으로 오해시키지 않음", () => {
  for (const checked of [false, undefined]) {
    const mobile = transitNearbyHtml({ busStops: [STOP], subways: [], subwayChecked: checked });
    const pc = transitPcHtml({ busStops: [STOP], subways: [], subwayChecked: checked });
    assert.doesNotMatch(mobile, /가까운 역|transit-none/);
    assert.doesNotMatch(pc, /transit-none|pcd-station/);
  }
});

test("역이 있으면 '없음' 줄 대신 역 줄", () => {
  const data = { busStops: [], subways: [{ name: "매교역", lines: ["수인분당선"], distanceMeters: 1300 }], subwayChecked: true };
  assert.doesNotMatch(transitNearbyHtml(data), /transit-none/);
  assert.doesNotMatch(transitPcHtml(data), /transit-none/);
});

test("정류장도 역도 없으면 검색이 끝났어도 칸 전체 숨김 (모바일 · PC)", () => {
  const data = { busStops: [], subways: [], subwayChecked: true };
  assert.strictEqual(transitNearbyHtml(data), "");
  assert.strictEqual(transitPcHtml(data), "");
  assert.strictEqual(transitPcHtml(null), "");
});

test("PC: 제목 '오시는 길' → 정류장 칸(라벨 · 이름 · 도보) → 칩 4개 + '+N' → 출처", () => {
  const html = transitPcHtml({ busStops: [STOP, { name: "팔달문", distanceMeters: 420, walkMinutes: 6, routes: ["13-4"] }], subways: [], subwayChecked: false });
  assert.strictEqual(TRANSIT_PC_ROUTE_LIMIT, 4);
  assert.match(html, /^<h3 class="pcd-transit-title">오시는 길<\/h3><ul class="pcd-stops">/);
  assert.strictEqual((html.match(/<li class="pcd-stop">/g) || []).length, 2);
  assert.match(html, /<span class="pcd-stop-label">정류장<\/span><span class="pcd-stop-name">행궁동<\/span><span class="pcd-stop-dist">도보 2분 · 140m<\/span>/);
  // 첫 정류장 7개 → 4개 보이고 3개 숨김 + "+3"
  assert.strictEqual((html.match(/class="transit-chip">/g) || []).length, 4 + 1);
  assert.strictEqual((html.match(/class="transit-chip" hidden>/g) || []).length, 3);
  assert.match(html, /aria-label="버스 3개 더 보기">\+3<\/button>/);
  assert.match(html, /<p class="transit-note">[^<]*경기도 버스정보/);
});

test("PC: 역은 한 줄 상자(이름 + 호선 · 직선거리), 없으면 '없음' 줄", () => {
  const withStation = transitPcHtml({ busStops: [], subways: [{ name: "매교역", lines: ["수인분당선"], distanceMeters: 1300 }], subwayChecked: true });
  assert.match(withStation, /<div class="pcd-station"><span class="transit-name">매교역 <span class="transit-lines">수인분당선<\/span><\/span><span class="transit-dist">직선 1\.3km<\/span><\/div>/);
  assert.doesNotMatch(withStation, /pcd-stops|경기도 버스정보/);
  const none = transitPcHtml({ busStops: [STOP], subways: [], subwayChecked: true });
  assert.match(none, /<div class="transit-none"><span>근처 지하철역 없음<\/span><span>1\.5km 안<\/span><\/div>/);
});

test("PC: 이름의 특수문자는 글자로", () => {
  const html = transitPcHtml({ busStops: [{ name: "<i>x</i>", distanceMeters: 1, walkMinutes: 1, routes: [] }], subways: [{ name: "<b>역</b>", lines: ["<u>"], distanceMeters: 1 }] });
  assert.doesNotMatch(html, /<i>|<b>|<u>/);
});
