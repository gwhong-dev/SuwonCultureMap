// 상세 화면 표시 규칙 시험 — 인스타 행사(info · 사진 없음)는 장소 · 시간 · 출처로 채우고, 재단 행사는 그대로
const test = require("node:test");
const assert = require("node:assert");
const {
  extractPlaceName,
  eventPlace,
  fallbackTime,
  sourceLabel,
  safeLink,
  linkButtonText,
  detailInfoText,
  cardImageUrl,
  cardFallbackInfo,
  detailFields,
  hasOriginalInfo,
} = require("../../main/resources/static/event-detail.js");

const instagram = {
  info: null,
  place: "가상 공연장",
  timeText: "오후 7시",
  source: "INSTAGRAM",
  sourceAccount: "sw_culture",
  link: "https://www.instagram.com/p/TEST/",
};
const foundation = {
  info: "장소: 가상 전시관\n시간: 10:00~18:00\n이용료: 무료",
  place: "다른 장소 칸",
  timeText: "다른 시간 칸",
  source: "SWCF",
  sourceAccount: null,
  link: "http://www.swcf.or.kr/?p=1",
};

test("info 가 없으면 PC 상세 본문은 장소 · 시간 · 출처 줄", () => {
  assert.strictEqual(detailInfoText(instagram), "장소: 가상 공연장\n시간: 오후 7시\n출처: 인스타그램 @sw_culture");
});

test("재단 행사 PC 상세 본문은 info 원문 그대로", () => {
  assert.strictEqual(detailInfoText(foundation), foundation.info);
});

test("값이 하나도 없으면 빈 본문", () => {
  assert.strictEqual(detailInfoText({ source: "SWCF" }), "");
});

test("장소: 서버가 꺼낸 place 칸 먼저, 비면 info 원문", () => {
  assert.strictEqual(eventPlace(foundation), "다른 장소 칸");
  assert.strictEqual(eventPlace(instagram), "가상 공연장");
  assert.strictEqual(eventPlace({ info: "주소: 수원시 팔달구", place: null }), "수원시 팔달구");
  assert.strictEqual(eventPlace({ info: "장소: 가상 전시관", place: "" }), "가상 전시관");
  assert.strictEqual(eventPlace({ info: null, place: null }), null);
});

test("시간: info 가 없을 때만 timeText", () => {
  assert.strictEqual(fallbackTime(instagram), "오후 7시");
  assert.strictEqual(fallbackTime(foundation), null);
});

test("출처는 인스타 행사만, 계정이 없으면 '인스타그램'", () => {
  assert.strictEqual(sourceLabel(instagram), "인스타그램 @sw_culture");
  assert.strictEqual(sourceLabel({ source: "INSTAGRAM", sourceAccount: null }), "인스타그램");
  assert.strictEqual(sourceLabel(foundation), null);
});

test("원문 링크는 http · https 만", () => {
  assert.strictEqual(safeLink(instagram.link), instagram.link);
  assert.strictEqual(safeLink(foundation.link), foundation.link);
  assert.strictEqual(safeLink("data:text/html,x"), null);
  assert.strictEqual(safeLink("//evil.example"), null);
  assert.strictEqual(safeLink(null), null);
});

test("링크 버튼 글: 인스타 행사는 인스타그램에서 보기", () => {
  assert.strictEqual(linkButtonText(instagram), "인스타그램에서 보기 →");
  assert.strictEqual(linkButtonText(foundation), "원문 보기 →");
});

test("extractPlaceName 은 서버(InfoParser)와 같이 정확한 라벨만, 장소 → 주소/소재지 → 주소 순", () => {
  assert.strictEqual(extractPlaceName("주소: 수원\n장소: 광장"), "광장");
  assert.strictEqual(extractPlaceName("행사장소: 광장\n주소: 수원"), "수원");
  assert.strictEqual(extractPlaceName("주소: 수원\n주소/소재지: 팔달구"), "팔달구");
  assert.strictEqual(extractPlaceName("장소: \n주소: 수원"), "수원");
  assert.strictEqual(extractPlaceName("주차장소: 없음"), null);
  assert.strictEqual(extractPlaceName("소개: 없음"), null);
  assert.strictEqual(extractPlaceName(null), null);
});

test("목록 카드 사진: 주소가 없거나 http · https 가 아니면 null (사진 칸 없이 그림)", () => {
  assert.strictEqual(cardImageUrl({ imageUrl: "https://www.swcf.or.kr/a.jpg" }), "https://www.swcf.or.kr/a.jpg");
  assert.strictEqual(cardImageUrl({ imageUrl: null }), null);
  assert.strictEqual(cardImageUrl({ imageUrl: "" }), null);
  assert.strictEqual(cardImageUrl(instagram), null);
  assert.strictEqual(cardImageUrl({ imageUrl: "javascript:alert(1)" }), null);
});

test("목록 카드 요약: info 가 없으면 장소 · 시간 줄 (출처는 상세에만)", () => {
  assert.strictEqual(cardFallbackInfo(instagram), "장소: 가상 공연장\n시간: 오후 7시");
  assert.strictEqual(cardFallbackInfo({ info: null, place: "광장" }), "장소: 광장");
  assert.strictEqual(cardFallbackInfo({ info: null }), "");
});

test("목록 카드 요약: 재단 행사(info 있음)는 빈 값 → 기존 이용료/장소/시간 요약을 씀", () => {
  assert.strictEqual(cardFallbackInfo(foundation), "");
});

test("detailFields: 서버가 분해한 요금 · 문의 · 주최 칸 (V7), 없으면 null → 화면이 원문에서 찾음", () => {
  assert.deepStrictEqual(detailFields({ fee: "무료", contact: "031-290-3600", host: "수원문화재단" }), {
    fee: "무료",
    contact: "031-290-3600",
    host: "수원문화재단",
  });
  // 동기화 전 옛 행(칸 없음) · 빈 글자
  assert.deepStrictEqual(detailFields({ info: "이용료: 무료" }), { fee: null, contact: null, host: null });
  assert.deepStrictEqual(detailFields({ fee: "", contact: null }), { fee: null, contact: null, host: null });
});

// "💸 무료" 칩 판정 — 운영 재단 요금 값(11/9 점검) 그대로
const { isFreeEvent, eventFeeText } = require("../../main/resources/static/event-detail.js");

test("무료 판정: 요금 칸(V7) 값이 무료면 무료, 유료가 섞이면 아님", () => {
  for (const fee of ["무료", "무 료", "전석 무료", "무료 입장", "무료(예약금 있으나 환불 예정)"]) {
    assert.strictEqual(isFreeEvent({ fee }), true, fee);
  }
  for (const fee of ["무료(일부 공연 및 프로그램 유료)", "무료(시민예술교육은 유료)", "전석 10,000원", "유료", "상세참조", "해당없음", "어린이 무료, 성인 10,000원", "무료~5,000원", "무료 / 일반 1만원"]) {
    assert.strictEqual(isFreeEvent({ fee }), false, fee);
  }
});

test("무료 판정: 요금 칸이 비면(동기화 전) 원문의 이용료 · 관람료 줄, 주차요금은 안 봄", () => {
  assert.strictEqual(isFreeEvent({ fee: null, info: "문의처: 031\n관람료: 무료\n장소: 수원" }), true);
  assert.strictEqual(isFreeEvent({ fee: null, info: "주차요금: 무료\n이용료: 전석 2만원" }), false);
  assert.strictEqual(isFreeEvent({ fee: null, info: "주차요금: 무료" }), false);
  assert.strictEqual(eventFeeText({ fee: "", info: "이용료: 전석 1만원" }), "전석 1만원");
});

test("무료 판정: 요금 정보가 없으면(인스타 행사 등) 무료 아님", () => {
  assert.strictEqual(isFreeEvent({ source: "INSTAGRAM", info: null, fee: null }), false);
  assert.strictEqual(isFreeEvent({}), false);
});

// 수원문화DB 행사: info 칸 = "여러 장소 · 원문 확인" 안내 (원문 아님) → 시간 · 출처 줄을 그대로 보여 줌
const swdbMulti = {
  info: "⚠ 장소가 여러 곳이에요. 날짜마다 장소가 다를 수 있어 원문에서 꼭 확인하세요.\n장소 전체: 가상홀, 나홀, 다홀",
  place: "여러 장소 · 가상홀 외 2곳",
  timeText: "10:30~20:30",
  source: "SWDB",
  link: "https://www.swcf.or.kr/swdb/?p=15_view&reqIdx=1&page=1",
};
const swdbSingle = { info: null, place: "수원시 팔달구 가상로 1", timeText: null, source: "SWDB" };

test("수원문화DB 출처 줄", () => {
  assert.strictEqual(sourceLabel(swdbMulti), "수원문화DB(수원문화재단)");
  assert.strictEqual(sourceLabel(swdbSingle), "수원문화DB(수원문화재단)");
});

test("수원문화DB 안내 글은 원문이 아니라 시간 · 카드 요약을 막지 않음", () => {
  assert.strictEqual(hasOriginalInfo(swdbMulti), false);
  assert.strictEqual(hasOriginalInfo(foundation), true);
  assert.strictEqual(fallbackTime(swdbMulti), "10:30~20:30");
  assert.strictEqual(cardFallbackInfo(swdbMulti), "장소: 여러 장소 · 가상홀 외 2곳\n시간: 10:30~20:30");
});

test("수원문화DB PC 상세 본문: 안내 글 → 장소 · 시간 · 출처", () => {
  assert.strictEqual(
    detailInfoText(swdbMulti),
    swdbMulti.info + "\n\n장소: 여러 장소 · 가상홀 외 2곳\n시간: 10:30~20:30\n출처: 수원문화DB(수원문화재단)"
  );
  assert.strictEqual(detailInfoText(swdbSingle), "장소: 수원시 팔달구 가상로 1\n출처: 수원문화DB(수원문화재단)");
  assert.strictEqual(linkButtonText(swdbMulti), "원문 보기 →");
});
