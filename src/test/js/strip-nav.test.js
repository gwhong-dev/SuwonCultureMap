// 지도 하단 카드 줄 마우스 이동 규칙 시험 — 좌우 화살표 · 휠로 한 장씩
const test = require("node:test");
const assert = require("node:assert");
const {
  WHEEL_GAP_MS,
  stripStepTarget,
  stripArrowState,
  wheelStep,
} = require("../../main/resources/static/strip-nav.js");

test("다음 · 이전: 한 장씩 움직임", () => {
  assert.strictEqual(stripStepTarget(2, 1, 5), 3);
  assert.strictEqual(stripStepTarget(2, -1, 5), 1);
});

test("다음 · 이전: 처음 · 끝을 넘지 않음", () => {
  assert.strictEqual(stripStepTarget(0, -1, 5), 0);
  assert.strictEqual(stripStepTarget(4, 1, 5), 4);
});

test("다음 · 이전: 현재 카드를 모르면(-1) 첫 카드 기준", () => {
  assert.strictEqual(stripStepTarget(-1, 1, 5), 1);
  assert.strictEqual(stripStepTarget(-1, -1, 5), 0);
});

test("다음 · 이전: 카드가 없으면 -1", () => {
  assert.strictEqual(stripStepTarget(0, 1, 0), -1);
});

test("화살표: 처음이면 이전 숨김 · 끝이면 다음 숨김", () => {
  assert.deepStrictEqual(stripArrowState(0, 5), { prev: false, next: true });
  assert.deepStrictEqual(stripArrowState(2, 5), { prev: true, next: true });
  assert.deepStrictEqual(stripArrowState(4, 5), { prev: true, next: false });
});

test("화살표: 카드 1장 · 0장이면 둘 다 숨김", () => {
  assert.deepStrictEqual(stripArrowState(0, 1), { prev: false, next: false });
  assert.deepStrictEqual(stripArrowState(-1, 0), { prev: false, next: false });
});

test("휠: 아래로 굴리면 다음 · 위로 굴리면 이전", () => {
  assert.strictEqual(wheelStep(0, 100, 0, 1000), 1);
  assert.strictEqual(wheelStep(0, -100, 0, 1000), -1);
});

test("휠: 가로가 더 크면(트랙패드 좌우) 브라우저 기본 스크롤에 맡김", () => {
  assert.strictEqual(wheelStep(40, 10, 0, 1000), 0);
});

test("휠: 직전 이동 뒤 잠깐은 무시 (한 번 굴림에 여러 장 넘어가지 않게)", () => {
  assert.strictEqual(wheelStep(0, 100, 1000, 1000 + WHEEL_GAP_MS - 1), 0);
  assert.strictEqual(wheelStep(0, 100, 1000, 1000 + WHEEL_GAP_MS), 1);
});
