// 창 포커스 이동 시험 — 열리면 첫 버튼으로, 닫히면 원래 자리로 (보조기기용)
const test = require("node:test");
const assert = require("node:assert");
const { firstFocusable, createDialogFocus } = require("../../main/resources/static/dialog-focus.js");

// 가짜 요소: shown = 화면에 보이는지
function el(name, shown = true) {
  return {
    name,
    shown,
    isConnected: true,
    disabled: false,
    getClientRects() {
      return this.shown ? [{}] : [];
    },
    focus() {
      doc.activeElement = this;
    },
  };
}
function box(children) {
  return {
    querySelectorAll: () => children,
    contains: (x) => children.includes(x),
  };
}
let doc;
function setup(active) {
  doc = { body: { name: "body" }, activeElement: active };
  doc.activeElement = active || doc.body;
  return createDialogFocus({ document: doc });
}

test("첫 버튼: 숨겨진 버튼(PC 전용 ✕ 등)은 건너뜀", () => {
  const hiddenClose = el("x", false);
  const back = el("back");
  assert.strictEqual(firstFocusable(box([hiddenClose, back])), back);
  assert.strictEqual(firstFocusable(box([el("a", false)])), null);
});

test("열리면 창의 첫 버튼으로, 닫히면 연 버튼으로 돌아감", () => {
  const card = el("card");
  const focus = setup(card);
  const close = el("close");
  focus.opened("route", box([close, el("kakao")]));
  assert.strictEqual(doc.activeElement, close);
  focus.closed("route");
  assert.strictEqual(doc.activeElement, card);
});

test("상세에서 다른 행사를 다시 열어도 처음 연 자리를 기억", () => {
  const card = el("card");
  const focus = setup(card);
  const back = el("back");
  const dialog = box([back]);
  focus.opened("detail", dialog);
  focus.opened("detail", dialog); // 이미 열린 상세를 다시 엶 (포커스는 창 안)
  focus.closed("detail");
  assert.strictEqual(doc.activeElement, card);
});

test("상세 위 공유 창: 닫으면 상세의 공유 버튼으로", () => {
  const card = el("card");
  const focus = setup(card);
  const shareBtn = el("shareBtn");
  focus.opened("detail", box([el("back"), shareBtn]));
  shareBtn.focus();
  focus.opened("share", box([el("shareClose")]));
  focus.closed("share");
  assert.strictEqual(doc.activeElement, shareBtn);
  focus.closed("detail");
  assert.strictEqual(doc.activeElement, card);
});

test("되돌릴 자리가 사라졌거나 숨겨졌으면 그대로 둠", () => {
  const card = el("card");
  const focus = setup(card);
  const close = el("close");
  focus.opened("detail", box([close]));
  card.shown = false; // 목록을 다시 그려 옛 카드가 사라짐
  focus.closed("detail");
  assert.strictEqual(doc.activeElement, close);
});

test("열리지 않은 창을 닫아도 아무것도 안 함 (상세를 닫을 때 공유 창도 닫음)", () => {
  const card = el("card");
  const focus = setup(card);
  focus.closed("share");
  assert.strictEqual(doc.activeElement, card);
});

test("포커스가 없던 상태(body)에서 열면 닫을 때 옮기지 않음", () => {
  const focus = setup(null);
  const close = el("close");
  focus.opened("route", box([close]));
  focus.closed("route");
  assert.strictEqual(doc.activeElement, close);
});

test("forget: 바깥을 눌러 닫은 창은 포커스를 옮기지 않고, 다음에 열면 새 자리를 기억 (PC 길찾기 작은 창)", () => {
  const firstButton = el("route-1");
  const secondButton = el("route-2");
  const close = el("close");
  const focus = setup(firstButton);
  focus.opened("routePop", box([close]));
  focus.forget("routePop");
  assert.strictEqual(doc.activeElement, close); // 그대로
  doc.activeElement = secondButton;
  focus.opened("routePop", box([close]));
  focus.closed("routePop");
  assert.strictEqual(doc.activeElement, secondButton);
});
