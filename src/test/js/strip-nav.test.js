// 지도 하단 카드 줄 마우스 이동 규칙 시험 — 좌우 화살표 · 휠로 한 장씩
const test = require("node:test");
const assert = require("node:assert");
const {
  WHEEL_GAP_MS,
  stripStepTarget,
  stripArrowState,
  wheelStep,
  stripSettleIndex,
  stripKeyStep,
} = require("../../main/resources/static/strip-nav.js");

test("키보드: → 다음 · ← 이전, 다른 키는 0", () => {
  assert.strictEqual(stripKeyStep({ key: "ArrowRight" }), 1);
  assert.strictEqual(stripKeyStep({ key: "ArrowLeft" }), -1);
  assert.strictEqual(stripKeyStep({ key: "ArrowDown" }), 0);
  assert.strictEqual(stripKeyStep({ key: "Enter" }), 0);
});

test("키보드: 입력칸 · 선택 상자에서 치는 중이면 넘기지 않음 (글자 사이 이동)", () => {
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", tag: "INPUT" }), 0);
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", tag: "TEXTAREA" }), 0);
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", tag: "SELECT" }), 0);
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", editable: true }), 0);
});

test("키보드: 탭 줄(role=tab) 처럼 자기 화살표 키를 쓰는 곳 · Alt 등 조합키 · 이미 처리된 키는 그대로 둠", () => {
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", role: "tab" }), 0);
  assert.strictEqual(stripKeyStep({ key: "ArrowLeft", alt: true }), 0); // 브라우저 뒤로 가기
  assert.strictEqual(stripKeyStep({ key: "ArrowRight", handled: true }), 0);
});

// 카드 420px · 사이 8px · 앞 여백 12px (PC 하단 카드)
const CARDS = [0, 1, 2, 3, 4].map((i) => ({ left: 12 + i * 428, width: 420 }));

test("멈춤: 넓은 줄에서 첫 카드를 골랐으면 맨 왼쪽(가운데로 못 옴)이어도 그대로", () => {
  // 줄 폭 1235px · 스크롤 0 → 가운데(617)에 더 가까운 건 2번째 카드지만 첫 카드가 다 보임
  assert.strictEqual(stripSettleIndex(0, CARDS, 0, 1235), 0);
});

test("멈춤: 넓은 줄에서 끝 카드를 골랐으면 그대로", () => {
  const end = 12 + 4 * 428 + 420 + 12 - 1235; // 끝까지 스크롤
  assert.strictEqual(stripSettleIndex(4, CARDS, end, 1235), 4);
});

test("멈춤: 고른 카드가 화면 밖으로 밀려나면 가운데에 가장 가까운 카드", () => {
  // 손으로 밀어 0번 카드가 왼쪽으로 반쯤 나감 → 줄 가운데 917 에 가장 가까운 건 index 2 (가운데 1078)
  assert.strictEqual(stripSettleIndex(0, CARDS, 300, 1235), 2);
});

test("멈춤: 휴대폰처럼 한 장만 다 보이는 줄은 가운데 카드 (예전과 같음)", () => {
  const phone = [0, 1, 2].map((i) => ({ left: 12 + i * 330, width: 322 }));
  // 1번 카드를 가운데로 밀어 둠 → 0번은 일부만 보임
  assert.strictEqual(stripSettleIndex(0, phone, 330 + 12 - 27, 375), 1);
});

test("멈춤: 고른 카드를 모르면(-1) 가운데 카드, 카드가 없으면 -1", () => {
  assert.strictEqual(stripSettleIndex(-1, CARDS, 0, 1235), 1);
  assert.strictEqual(stripSettleIndex(0, [], 0, 1235), -1);
});

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
