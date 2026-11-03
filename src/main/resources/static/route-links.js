// 길찾기 링크 (디자인 docs/design/mobile-design-v1.md 3번) — 지도 앱은 URL 로 열고, API 키는 쓰지 않음
// 시험: node --test src/test/js/ (CI 에서 실행)

// 카카오맵 웹 "목적지로 길찾기" 링크 (map.kakao.com/link/to/이름,위도,경도) — 수단을 고를 수 없어 자동차로 열림
// → 폰에서는 아래 앱 주소를 먼저 시도하고, 앱이 없을 때만 이 링크로 넘어감 (index.html openAppOrWeb)
function kakaoRouteUrl(name, lat, lng) {
  return "https://map.kakao.com/link/to/" + encodeURIComponent(name) + "," + lat + "," + lng;
}

// 카카오맵 앱 길찾기 (kakaomap://route): 출발(sp) 비움 = 현재 위치, 도착(ep) = 위도,경도, by = 대중교통
function kakaoRouteAppUrl(lat, lng) {
  return "kakaomap://route?ep=" + lat + "," + lng + "&by=PUBLICTRANSIT";
}

// 네이버 지도 웹 길찾기 주소는 좌표를 웹 메르카토르(EPSG:3857, 미터) 로 받음
var EARTH_HALF = 20037508.34;
function webMercator(lat, lng) {
  var x = (lng * EARTH_HALF) / 180;
  var y = (Math.log(Math.tan(((90 + lat) * Math.PI) / 360)) * EARTH_HALF) / Math.PI;
  return { x: Math.round(x * 100) / 100 || 0, y: Math.round(y * 100) / 100 || 0 }; // || 0: -0 방지
}

// 네이버 지도 길찾기 (출발 = 현재 위치 비움, 도착 = 좌표 + 이름, 대중교통)
function naverRouteUrl(name, lat, lng) {
  var p = webMercator(lat, lng);
  return (
    "https://map.naver.com/p/directions/-/" + p.x + "," + p.y + "," + encodeURIComponent(name) + ",,/-/transit"
  );
}

// Google Maps 길찾기 — 한국은 대중교통만 지원 (도보 · 자동차 미지원)
function googleRouteUrl(lat, lng) {
  return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng + "&travelmode=transit";
}

// info 원문에서 주소 줄 ("주소" · "소재지" 라벨). 없으면 null
function extractAddress(infoText) {
  if (!infoText) return null;
  var lines = infoText.split("\n");
  for (var i = 0; i < lines.length; i++) {
    var idx = lines[i].indexOf(":");
    if (idx === -1) continue;
    var label = lines[i].substring(0, idx).trim();
    var value = lines[i].substring(idx + 1).trim();
    if (value && (label.indexOf("주소") !== -1 || label.indexOf("소재지") !== -1)) return value;
  }
  return null;
}

if (typeof module !== "undefined") {
  module.exports = {
    kakaoRouteUrl: kakaoRouteUrl,
    kakaoRouteAppUrl: kakaoRouteAppUrl,
    webMercator: webMercator,
    naverRouteUrl: naverRouteUrl,
    googleRouteUrl: googleRouteUrl,
    extractAddress: extractAddress,
  };
}
