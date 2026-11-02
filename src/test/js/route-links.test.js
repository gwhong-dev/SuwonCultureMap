// 길찾기 링크 시험: node --test src/test/js/
const test = require("node:test");
const assert = require("node:assert");
const { kakaoRouteUrl, webMercator, naverRouteUrl, googleRouteUrl, extractAddress } =
  require("../../main/resources/static/route-links.js");

// 화성행궁 (정조로 825)
const LAT = 37.2816, LNG = 127.0137;

test("카카오맵: 이름은 URL 인코딩, 위도 · 경도 순서", () => {
  assert.strictEqual(kakaoRouteUrl("화성행궁", LAT, LNG),
    "https://map.kakao.com/link/to/%ED%99%94%EC%84%B1%ED%96%89%EA%B6%81,37.2816,127.0137");
});

test("이름에 쉼표 · 공백 · & 가 있어도 주소가 깨지지 않음", () => {
  const url = kakaoRouteUrl("행궁 광장, 앞 & 뒤", LAT, LNG);
  assert.ok(url.includes("%2C"), "쉼표가 인코딩됨");
  assert.ok(!url.includes("&"), "& 가 인코딩됨");
  assert.ok(naverRouteUrl("행궁 광장, 앞", LAT, LNG).includes("%2C"));
});

test("웹 메르카토르 변환 (x = 경도 비례, y = ln(tan) — 수원은 약 14,139,100 · 4,478,400m)", () => {
  const p = webMercator(LAT, LNG);
  assert.ok(Math.abs(p.x - 14139100) < 100, "x=" + p.x);
  assert.ok(Math.abs(p.y - 4478431) < 10, "y=" + p.y);
  assert.deepStrictEqual(webMercator(0, 0), { x: 0, y: 0 });
});

test("네이버 지도: 출발 비움, 도착 좌표 + 이름, 대중교통", () => {
  const url = naverRouteUrl("화성행궁", LAT, LNG);
  assert.ok(url.startsWith("https://map.naver.com/p/directions/-/"));
  assert.ok(url.endsWith(",%ED%99%94%EC%84%B1%ED%96%89%EA%B6%81,,/-/transit"));
});

test("Google Maps: 좌표 목적지 + 대중교통", () => {
  assert.strictEqual(googleRouteUrl(LAT, LNG),
    "https://www.google.com/maps/dir/?api=1&destination=37.2816,127.0137&travelmode=transit");
});

test("주소 줄은 '주소' · '소재지' 라벨에서, 없으면 null", () => {
  assert.strictEqual(extractAddress("장소: 화성행궁\n주소: 경기도 수원시 팔달구 정조로 825"), "경기도 수원시 팔달구 정조로 825");
  assert.strictEqual(extractAddress("행사 소재지: 수원시 장안구 송정로 19"), "수원시 장안구 송정로 19");
  assert.strictEqual(extractAddress("장소: 화성행궁"), null);
  assert.strictEqual(extractAddress("주소: "), null);
  assert.strictEqual(extractAddress(null), null);
});
