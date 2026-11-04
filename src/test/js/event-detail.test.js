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

test("장소: info 가 있으면 원문에서만, 없으면 place 칸", () => {
  assert.strictEqual(eventPlace(foundation), "가상 전시관");
  assert.strictEqual(eventPlace(instagram), "가상 공연장");
  assert.strictEqual(eventPlace({ info: "주소: 수원시 팔달구", place: "칸" }), "수원시 팔달구");
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

test("extractPlaceName 은 옮긴 뒤에도 장소 라벨 → 주소 순", () => {
  assert.strictEqual(extractPlaceName("행사장소: 광장\n주소: 수원"), "광장");
  assert.strictEqual(extractPlaceName("소개: 없음"), null);
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
