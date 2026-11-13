// 첫 진입 애니메이션 B안 시험 (11/13 확정) — intro.js · css/intro.css · index.html 연결
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  INTRO_SEEN_KEY,
  INTRO_TOTAL_MS,
  INTRO_FADE_MS,
  INTRO_PETAL_COUNT,
  isIntroPreview,
  introSeason,
  readIntroSeen,
  markIntroSeen,
  shouldPlayIntro,
  introHtml,
  startIntro,
} = require("../../main/resources/static/intro.js");

const STATIC = path.join(__dirname, "../../main/resources/static");
const read = (name) => fs.readFileSync(path.join(STATIC, name), "utf8").replace(/\r\n/g, "\n");
const introCss = read("css/intro.css");
const introJs = read("intro.js");
const indexHtml = read("index.html");

// 가짜 localStorage. mode = "ok" | "readBlocked"(읽기 오류) | "writeBlocked"(쓰기 오류, 예: 저장 공간 꽉 참)
function fakeStorage(initial, mode) {
  const data = Object.assign({}, initial);
  return {
    data,
    getItem(k) {
      if (mode === "readBlocked") throw new Error("SecurityError");
      return Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null;
    },
    setItem(k, v) {
      if (mode === "writeBlocked") throw new Error("QuotaExceededError");
      data[k] = String(v);
    },
  };
}

test("미리 보기 ?intro=b 만, 옛 a · c 와 다른 값은 무시", () => {
  assert.strictEqual(isIntroPreview("?intro=b"), true);
  assert.strictEqual(isIntroPreview("?q=1&intro=B"), true);
  assert.strictEqual(isIntroPreview("?intro=b&season=spring"), true);
  for (const s of ["", "?", "?intro=", "?intro=a", "?intro=c", "?intro=bb", "?xintro=b", "?intro=%E0%A4%A", null, undefined]) {
    assert.strictEqual(isIntroPreview(s), false, String(s));
  }
});

test("본 적 기록: 읽기 · 쓰기, 저장소가 없거나 막히면 null · false", () => {
  assert.strictEqual(readIntroSeen(fakeStorage({})), false);
  assert.strictEqual(readIntroSeen(fakeStorage({ [INTRO_SEEN_KEY]: "1" })), true);
  assert.strictEqual(readIntroSeen(fakeStorage({}, "readBlocked")), null);
  assert.strictEqual(readIntroSeen(null), null);
  const s = fakeStorage({});
  assert.strictEqual(markIntroSeen(s), true);
  assert.strictEqual(s.data[INTRO_SEEN_KEY], "1");
  assert.strictEqual(markIntroSeen(fakeStorage({}, "writeBlocked")), false);
  assert.strictEqual(markIntroSeen(null), false);
});

test("재생 판단: 처음만 재생, 본 적 있음 · 저장소 막힘은 안 함, ?intro=b 는 늘, 움직임 줄이기는 늘 안 함", () => {
  assert.strictEqual(shouldPlayIntro("", false, false), true);
  assert.strictEqual(shouldPlayIntro("", false, true), false);
  assert.strictEqual(shouldPlayIntro("", false, null), false);
  assert.strictEqual(shouldPlayIntro("?intro=b", false, true), true);
  assert.strictEqual(shouldPlayIntro("?intro=b", false, null), true);
  assert.strictEqual(shouldPlayIntro("?intro=a", false, true), false);
  assert.strictEqual(shouldPlayIntro("?intro=c", false, true), false);
  assert.strictEqual(shouldPlayIntro("", true, false), false);
  assert.strictEqual(shouldPlayIntro("?intro=b", true, false), false);
});

test("계절: 3~8월 벚꽃 · 9~2월 은행잎, ?season= 으로 미리 보기", () => {
  for (const m of [3, 4, 8]) assert.strictEqual(introSeason(m, ""), "spring", String(m));
  for (const m of [9, 11, 12, 1, 2]) assert.strictEqual(introSeason(m, ""), "autumn", String(m));
  assert.strictEqual(introSeason(11, "?intro=b&season=spring"), "spring");
  assert.strictEqual(introSeason(4, "?intro=b&season=autumn"), "autumn");
  assert.strictEqual(introSeason(11, "?intro=b&season=winter"), "autumn"); // 모르는 값은 달로
});

test("덮개 HTML: 꽃잎 12장 + 계절 클래스 + 로고, 장식 숨김, CSP 때문에 style 속성 · 스크립트 없음", () => {
  const spring = introHtml("spring");
  const autumn = introHtml("autumn");
  assert.match(spring, /^<div class="intro ib-spring" aria-hidden="true">/);
  assert.match(autumn, /^<div class="intro ib-autumn" aria-hidden="true">/);
  assert.match(introHtml("winter"), /^<div class="intro ib-spring"/); // 모르는 값은 벚꽃
  assert.strictEqual(spring.match(/class="ib-petal ib-p\d+"/g).length, INTRO_PETAL_COUNT);
  assert.notStrictEqual(spring.match(/<path d="[^"]+"/)[0], autumn.match(/<path d="[^"]+"/)[0]); // 꽃잎 모양이 다름
  assert.match(spring, /<div class="intro-logo">/);
  assert.match(spring, /logo.svg#wordmark/);
  for (const html of [spring, autumn]) {
    assert.doesNotMatch(html, /style=/i);
    assert.doesNotMatch(html, /<script|on[a-z]+=/i);
  }
});

test("A · C안 코드가 남아 있지 않음 (이력은 docs/decisions/2026-11-13-intro-animation-b.md)", () => {
  for (const src of [introJs, introCss]) {
    assert.doesNotMatch(src, /\bia-|\bic-|\bix-|intro-[ac]\b/);
  }
});

test("css: 꽃잎 12장 자리 · 시간 1.5초 · 사라짐 0.3초 · 움직임 줄이기면 숨김", () => {
  for (let i = 1; i <= INTRO_PETAL_COUNT; i++) assert.match(introCss, new RegExp("\\.ib-p" + i + " \\{ --x0"), String(i));
  assert.strictEqual(INTRO_TOTAL_MS, 1500);
  assert.strictEqual(INTRO_FADE_MS, 300);
  const anims = introCss.match(/animation: [^;]+;/g) || [];
  assert.strictEqual(anims.length, 2); // 꽃잎 · 로고
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
// storage: fakeStorage(...) | null(저장소 없음) | "throw"(localStorage 를 꺼내는 순간 오류 — 쿠키 차단 등)
function fakeEnv(search, reduced, storage) {
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
  if (storage === "throw") {
    Object.defineProperty(win, "localStorage", { get() { throw new Error("SecurityError"); } });
  } else {
    win.localStorage = storage === undefined ? fakeStorage({}) : storage;
  }
  return { win, doc, el, timers, listeners, appended };
}

test("처음 방문: 재생하고 본 것으로 기록 → 두 번째 방문은 재생 안 함", () => {
  const storage = fakeStorage({});
  const first = fakeEnv("", false, storage);
  assert.ok(startIntro(first.win, first.doc));
  assert.strictEqual(first.appended.length, 1);
  assert.strictEqual(storage.data[INTRO_SEEN_KEY], "1", "재생을 시작하자마자 기록 (건너뛰어도 본 것)");

  const second = fakeEnv("", false, storage);
  assert.strictEqual(startIntro(second.win, second.doc), null);
  assert.strictEqual(second.appended.length, 0);
  assert.strictEqual(second.timers.length, 0);
});

test("저장소가 막혀 있으면 재생 안 함 (읽기 오류 · 쓰기 오류 · 꺼내기 오류 · 없음)", () => {
  for (const storage of [fakeStorage({}, "readBlocked"), fakeStorage({}, "writeBlocked"), "throw", null]) {
    const env = fakeEnv("", false, storage);
    assert.strictEqual(startIntro(env.win, env.doc), null, String(storage && storage.getItem ? "fake" : storage));
    assert.strictEqual(env.appended.length, 0);
  }
});

test("?intro=b 는 본 적 있어도 · 저장소가 막혀도 매번 재생", () => {
  for (const storage of [fakeStorage({ [INTRO_SEEN_KEY]: "1" }), fakeStorage({}, "writeBlocked"), "throw"]) {
    const env = fakeEnv("?intro=b&season=autumn", false, storage);
    assert.ok(startIntro(env.win, env.doc));
    assert.match(env.el.html, /ib-autumn/);
  }
});

test("?intro=a · ?intro=c 는 본 적 있으면 재생 안 함 (예전 미리 보기 주소 무시)", () => {
  for (const search of ["?intro=a", "?intro=c"]) {
    const env = fakeEnv(search, false, fakeStorage({ [INTRO_SEEN_KEY]: "1" }));
    assert.strictEqual(startIntro(env.win, env.doc), null, search);
  }
});

test("움직임 줄이기 설정이면 처음이어도 재생 안 함 · 기록도 안 함", () => {
  const storage = fakeStorage({});
  const env = fakeEnv("", true, storage);
  assert.strictEqual(startIntro(env.win, env.doc), null);
  assert.strictEqual(storage.data[INTRO_SEEN_KEY], undefined);
});

test("재생: 덮개를 붙이고 1.5초 뒤 사라짐 → 0.3초 뒤 지움", () => {
  const env = fakeEnv("?intro=b", false);
  const r = startIntro(env.win, env.doc);
  assert.ok(r);
  assert.strictEqual(env.appended.length, 1);
  assert.match(env.el.html, /class="intro ib-/);
  assert.strictEqual(env.timers[0].ms, 1500);
  env.timers[0].f();
  assert.deepStrictEqual(env.el.classes, ["intro-out"]);
  assert.strictEqual(env.timers[1].ms, 300);
  assert.strictEqual(env.listeners.keydown, undefined, "키 듣기 해제");
  env.timers[1].f();
  assert.strictEqual(env.appended.length, 0);
});

test("누르거나 키를 누르면 바로 건너뜀, 두 번 눌러도 한 번만 처리", () => {
  const env = fakeEnv("", false);
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
