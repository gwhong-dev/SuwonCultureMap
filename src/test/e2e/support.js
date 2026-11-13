// 화면 시험 공통 준비 — 외부 네트워크 막기 · 가짜 카카오 SDK · 가짜 행사 3건
// 앱(test 프로필)은 진짜로 떠 있고 화면 파일 · 보안 헤더(CSP)는 진짜를 씀. 행사 목록만 고정 값으로 바꿔
// 날짜 · 동기화 결과와 상관없이 늘 같은 화면에서 시험한다.
const path = require("path");
const base = require("@playwright/test");

const FAKE_KAKAO = path.join(__dirname, "fake-kakao.js");

// 한국 시간 오늘 기준 날짜 (n 일 뒤, "yyyy-MM-dd")
function kstDate(offsetDays) {
  const now = new Date(Date.now() + 9 * 3600 * 1000 + offsetDays * 86400 * 1000);
  return now.toISOString().slice(0, 10);
}

function event(id, title, category, lat, lng, place, address, startOffset, endOffset) {
  return {
    id,
    title,
    category,
    latitude: lat,
    longitude: lng,
    imageUrl: null, // 사진 대신 기본 그림 (외부 사진 주소를 부르지 않게)
    info: "장소 : " + place + "\n주소 : " + address,
    pubDate: kstDate(-10) + "T09:00:00",
    startDate: kstDate(startOffset),
    endDate: kstDate(endOffset),
    link: null,
    scheduleStatus: null,
    scheduleText: null,
    scheduleHolidayNote: false,
    dates: [],
    confirmedDates: [],
    place,
    timeText: null,
    fee: "무료",
    contact: null,
    host: "수원문화재단",
    source: "SWCF",
    sourceAccount: null,
    district: "팔달구",
  };
}

// 모두 진행 중 (오늘이 기간 안) — PC 기본 목록 · 모바일 카드 줄에 셋 다 보임
const EVENTS = [
  event(9001, "시험 공연 하나", "공연", 37.2818, 127.0145, "화성행궁", "경기도 수원시 팔달구 정조로 825", -3, 5),
  event(9002, "시험 전시 둘", "전시", 37.2665, 127.0005, "수원시립미술관", "경기도 수원시 팔달구 정조로 833", -1, 20),
  event(9003, "시험 축제 셋", "축제", 37.2871, 127.0196, "장안문", "경기도 수원시 장안구 영화동", 0, 2),
];

// 페이지 열기 전 한 번: 네트워크 길 정하기 + 첫 진입 애니메이션 건너뛰기
async function prepare(page) {
  const context = page.context();
  // 외부 주소는 전부 막고, 카카오 지도 SDK 로더만 가짜 파일로 대신 줌
  // (공유 SDK t1.kakaocdn.net 은 integrity 검사로 가짜를 못 넣음 → 막고, 가짜 로더가 window.Kakao 도 만듦)
  await context.route(
    (url) => url.hostname !== "localhost" && url.hostname !== "127.0.0.1",
    (route) => {
      const host = new URL(route.request().url()).hostname;
      if (host === "dapi.kakao.com") {
        return route.fulfill({ path: FAKE_KAKAO, contentType: "application/javascript" });
      }
      return route.abort("blockedbyclient");
    },
  );
  // 행사 목록 = 고정 3건. 주변 정류장 · 역(/transit)은 서버가 외부 API 를 부르므로 빈 결과(칸 숨김)로
  await page.route("**/api/events", (route) => route.fulfill({ json: EVENTS }));
  await page.route("**/api/events/*/transit", (route) => route.fulfill({ json: { busStops: [], subways: [] } }));
  await page.addInitScript(() => {
    try {
      localStorage.setItem("introSeen", "1");
    } catch (e) {
      /* 저장소를 못 써도 reducedMotion 으로 애니메이션은 안 뜸 */
    }
  });
}

// 첫 화면을 열고 행사가 그려질 때까지 기다림
async function openHome(page) {
  await prepare(page);
  await page.goto("/");
}

// 모바일: 하단 탭 [지도] 로 가서 카드 줄이 그려질 때까지 (모바일 첫 화면은 홈 탭)
async function openMobileMap(page) {
  await openHome(page);
  await page.locator('#tabBar [data-tab="map"]').click();
  await base.expect(page.locator("#previewStrip [data-event-id]")).toHaveCount(EVENTS.length);
}

// 화면 스크립트 오류가 나면 시험을 실패로 (누르는 동작은 통과해도 그리기가 깨진 걸 놓치지 않게)
//  - 막아 둔 외부 주소(카카오 공유 SDK 등)의 "Failed to load resource" 는 뺌
const test = base.test.extend({
  page: async ({ page }, use) => {
    const errors = [];
    page.on("pageerror", (e) => errors.push("pageerror: " + e.message));
    page.on("console", (m) => {
      if (m.type() === "error" && !/ERR_BLOCKED_BY_CLIENT/.test(m.text())) errors.push("console: " + m.text());
    });
    await use(page);
    base.expect(errors, "화면 스크립트 오류").toEqual([]);
  },
});

module.exports = { test, expect: base.expect, EVENTS, prepare, openHome, openMobileMap };
