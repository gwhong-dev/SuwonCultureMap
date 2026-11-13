// 첫 진입 애니메이션 3안 시험 (확정 시안 P7) — intro.js · css/intro.css · index.html 연결
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  INTRO_TOTAL_MS,
  INTRO_FADE_MS,
  INTRO_PETAL_COUNT,
  introVariant,
  introSeason,
  shouldPlayIntro,
  introHtml,
  startIntro,
} = require("../../main/resources/static/intro.js");

const STATIC = path.join(__dirname, "../../main/resources/static");
const read = (name) => fs.readFileSync(path.join(STATIC, name), "utf8").replace(/\r\n/g, "\n");
const introCss = read("css/intro.css");
const indexHtml = read("index.html");

test("?intro= 값: a · b · c 만 재생, 대소문자 · 다른 값과 섞여도 읽음", () => {
  assert.strictEqual(introVariant("?intro=a"), "a");
  assert.strictEqual(introVariant("?q=1&intro=c"), "c");
  assert.strictEqual(introVariant("?intro=B"), "b");
  assert.strictEqual(introVariant("?intro=b&season=spring"), "b");
  for (const s of ["", "?", "?intro=", "?intro=d", "?intro=ab", "?xintro=a", "?intro=%E0%A4%A", null, undefined]) {
    assert.strictEqual(introVariant(s), null, String(s));
  }
});

test("그냥 들어오거나 움직임 줄이기 설정이면 재생 안 함", () => {
  assert.strictEqual(shouldPlayIntro("", false), false);
  assert.strictEqual(shouldPlayIntro("?intro=a", false), true);
  assert.strictEqual(shouldPlayIntro("?intro=a", true), false);
});

test("2안 계절: 3~8월 벚꽃 · 9~2월 은행잎, ?season= 으로 미리 보기", () => {
  for (const m of [3, 4, 8]) assert.strictEqual(introSeason(m, ""), "spring", String(m));
  for (const m of [9, 11, 12, 1, 2]) assert.strictEqual(introSeason(m, ""), "autumn", String(m));
  assert.strictEqual(introSeason(11, "?intro=b&season=spring"), "spring");
  assert.strictEqual(introSeason(4, "?intro=b&season=autumn"), "autumn");
  assert.strictEqual(introSeason(11, "?intro=b&season=winter"), "autumn"); // 모르는 값은 달로
});

test("덮개 HTML: 안마다 클래스 · 장식 숨김, CSP 때문에 style 속성 · 스크립트 없음", () => {
  for (const v of ["a", "b", "c"]) {
    const html = introHtml(v, "spring");
    assert.match(html, new RegExp('^<div class="intro intro-' + v + '[ "]'));
    assert.match(html, /aria-hidden="true"/);
    assert.doesNotMatch(html, /style=/i, v);
    assert.doesNotMatch(html, /<script|on[a-z]+=/i, v);
  }
  assert.strictEqual(introHtml("d", "spring"), "");
  assert.strictEqual(introHtml(null, "spring"), "");
});

test("1안: 성문 두 짝 · 도장 · 확대 묶음", () => {
  const html = introHtml("a");
  assert.match(html, /class="ia-door ia-door-l"/);
  assert.match(html, /class="ia-door ia-door-r"/);
  assert.match(html, /<use class="ia-seal" href="\/brand\/logo.svg#seal"/);
  assert.match(html, /<g class="ia-zoom">/);
});

test("2안: 꽃잎 12장 + 계절 클래스 + 로고", () => {
  const spring = introHtml("b", "spring");
  const autumn = introHtml("b", "autumn");
  assert.strictEqual(spring.match(/class="ib-petal ib-p\d+"/g).length, INTRO_PETAL_COUNT);
  assert.match(spring, /^<div class="intro intro-b ib-spring"/);
  assert.match(autumn, /^<div class="intro intro-b ib-autumn"/);
  assert.notStrictEqual(spring.match(/<path d="[^"]+"/)[0], autumn.match(/<path d="[^"]+"/)[0]); // 꽃잎 모양이 다름
  assert.match(spring, /<div class="intro-logo">/);
  assert.match(spring, /logo.svg#wordmark/);
});

test("3안: 장안문 → 방화수류정 → 로고 순서", () => {
  const html = introHtml("c");
  const gate = html.indexOf('class="ic-gate"');
  const pavilion = html.indexOf('class="ic-pavilion"');
  const logo = html.indexOf('class="intro-logo"');
  assert.ok(gate > 0 && pavilion > gate && logo > pavilion);
});

test("css: 꽃잎 12장 자리 · 시간 1.5초 · 사라짐 0.3초 · 움직임 줄이기면 숨김", () => {
  for (let i = 1; i <= INTRO_PETAL_COUNT; i++) assert.match(introCss, new RegExp("\\.ib-p" + i + " \\{ --x0"), String(i));
  assert.strictEqual(INTRO_TOTAL_MS, 1500);
  assert.strictEqual(INTRO_FADE_MS, 300);
  const anims = introCss.match(/animation: [^;]+;/g) || [];
  assert.ok(anims.length >= 6);
  for (const a of anims) {
    const times = a.match(/\d+(\.\d+)?s\b/g) || [];
    assert.deepStrictEqual(times, ["1.5s"], a); // 모든 움직임이 1.5초 (intro.js INTRO_TOTAL_MS)
  }
  assert.match(introCss, /transition: opacity 0\.3s/);
  assert.match(introCss, /@media \(prefers-reduced-motion: reduce\) \{\n  \.intro \{\n    display: none;/);
});

test("index.html: intro.css 는 head, intro.js 는 body 맨 앞(지도 · 상단 바보다 먼저)에서 동기로", () => {
  const head = indexHtml.slice(0, indexHtml.indexOf("<body>"));
  assert.match(head, /<link rel="stylesheet" href="\/css\/intro.css" \/>/);
  const afterBody = indexHtml.slice(indexHtml.indexOf("<body>"));
  const scriptAt = afterBody.indexOf('<script src="/intro.js"></script>');
  assert.ok(scriptAt > 0, "intro.js 연결");
  assert.ok(scriptAt < afterBody.indexOf('<div id="topbar">'), "상단 바보다 먼저");
  assert.ok(scriptAt < afterBody.indexOf("<div"), "body 첫 요소보다 먼저");
});

// ----- 재생 흐름: 가짜 window · document -----
function fakeEnv(search, reduced) {
  const timers = [];
  const listeners = {};
  const appended = [];
  const el = {
    parentNode: null,
    classes: [],
    handlers: {},
    classList: { add(c) { el.classes.push(c); } },
    addEventListener(t, f) { el.handlers[t] = f; },
  };
  const body = {
    appendChild(node) { node.parentNode = body; appended.push(node); },
    removeChild(node) { node.parentNode = null; appended.splice(appended.indexOf(node), 1); },
  };
  const doc = {
    body,
    createElement() {
      return { set innerHTML(v) { el.html = v; this.firstChild = el; } };
    },
    addEventListener(t, f) { listeners[t] = f; },
    removeEventListener(t, f) { if (listeners[t] === f) delete listeners[t]; },
  };
  const win = {
    location: { search },
    matchMedia: () => ({ matches: reduced }),
    setTimeout(f, ms) { timers.push({ f, ms }); return timers.length; },
    clearTimeout(id) { if (timers[id - 1]) timers[id - 1].cleared = true; },
  };
  return { win, doc, el, timers, listeners, appended };
}

test("재생 안 할 때는 화면을 건드리지 않음", () => {
  for (const [search, reduced] of [["", false], ["?intro=x", false], ["?intro=a", true]]) {
    const env = fakeEnv(search, reduced);
    assert.strictEqual(startIntro(env.win, env.doc), null);
    assert.strictEqual(env.appended.length, 0);
    assert.strictEqual(env.timers.length, 0);
  }
});

test("재생: 덮개를 붙이고 1.5초 뒤 사라짐 → 0.3초 뒤 지움", () => {
  const env = fakeEnv("?intro=c", false);
  const r = startIntro(env.win, env.doc);
  assert.ok(r);
  assert.strictEqual(env.appended.length, 1);
  assert.match(env.el.html, /intro-c/);
  assert.strictEqual(env.timers[0].ms, 1500);
  env.timers[0].f();
  assert.deepStrictEqual(env.el.classes, ["intro-out"]);
  assert.strictEqual(env.timers[1].ms, 300);
  assert.strictEqual(env.listeners.keydown, undefined, "키 듣기 해제");
  env.timers[1].f();
  assert.strictEqual(env.appended.length, 0);
});

test("누르거나 키를 누르면 바로 건너뜀, 두 번 눌러도 한 번만 처리", () => {
  const env = fakeEnv("?intro=a", false);
  startIntro(env.win, env.doc);
  env.el.handlers.click();
  assert.ok(env.timers[0].cleared, "1.5초 타이머 취소");
  env.el.handlers.click();
  assert.deepStrictEqual(env.el.classes, ["intro-out"]);
  assert.strictEqual(env.timers.length, 2); // 1.5초 + 지우기 1개

  const env2 = fakeEnv("?intro=b", false);
  startIntro(env2.win, env2.doc);
  const key = { type: "keydown", stopped: false, prevented: false };
  key.preventDefault = () => { key.prevented = true; };
  key.stopPropagation = () => { key.stopped = true; };
  env2.listeners.keydown(key);
  assert.deepStrictEqual(env2.el.classes, ["intro-out"]);
  assert.ok(key.prevented && key.stopped, "건너뛴 키가 아래 화면(카드 넘기기 · Esc 닫기)까지 가지 않음");
});

test("어두운 테마에서도 1안 문 안(--bg)이 덮개 바탕(--bar)과 다른 색", () => {
  const theme = read("css/theme.css");
  assert.match(introCss, /\.ix-hole \{\n  fill: var\(--bg\);/);
  const dark = theme.slice(theme.indexOf('[data-theme="dark"]'));
  const val = (name) => dark.match(new RegExp("--" + name + ": (#[0-9a-f]+)"))[1];
  assert.notStrictEqual(val("bg"), val("bar"));
});
