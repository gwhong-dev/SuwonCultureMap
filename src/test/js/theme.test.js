// 화면 모드 저장 · 읽기 · 단추 규칙 시험 (theme.js)
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  THEME_KEY,
  normalizeMode,
  readMode,
  saveMode,
  themeAttr,
  isDarkMode,
  toggledMode,
  toggleLabel,
  applyMode,
  initThemeControls,
} = require("../../main/resources/static/theme.js");

function memoryStorage(initial) {
  const data = Object.assign({}, initial);
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => {
      data[k] = String(v);
    },
  };
}
const brokenStorage = {
  getItem() {
    throw new Error("SecurityError");
  },
  setItem() {
    throw new Error("QuotaExceededError");
  },
};

test("저장 키는 theme", () => {
  assert.strictEqual(THEME_KEY, "theme");
});

test("모드 값: system · light · dark 만, 나머지는 system", () => {
  assert.strictEqual(normalizeMode("light"), "light");
  assert.strictEqual(normalizeMode("dark"), "dark");
  assert.strictEqual(normalizeMode("system"), "system");
  assert.strictEqual(normalizeMode(null), "system");
  assert.strictEqual(normalizeMode("Dark"), "system");
  assert.strictEqual(normalizeMode(""), "system");
});

test("읽기: 저장값 그대로, 없거나 저장소가 막히면 system", () => {
  assert.strictEqual(readMode(memoryStorage({ theme: "dark" })), "dark");
  assert.strictEqual(readMode(memoryStorage({})), "system");
  assert.strictEqual(readMode(memoryStorage({ theme: "blue" })), "system");
  assert.strictEqual(readMode(null), "system");
  assert.strictEqual(readMode(brokenStorage), "system");
});

test("저장: 고른 모드를 그대로, 틀린 값은 system, 저장소가 막혀도 멈추지 않음", () => {
  const s = memoryStorage({});
  assert.strictEqual(saveMode(s, "light"), "light");
  assert.strictEqual(s.data.theme, "light");
  assert.strictEqual(saveMode(s, "system"), "system");
  assert.strictEqual(s.data.theme, "system");
  assert.strictEqual(saveMode(s, "x"), "system");
  assert.strictEqual(saveMode(brokenStorage, "dark"), "dark");
});

test("<html data-theme>: system 이면 속성 없음", () => {
  assert.strictEqual(themeAttr("light"), "light");
  assert.strictEqual(themeAttr("dark"), "dark");
  assert.strictEqual(themeAttr("system"), null);

  const attrs = {};
  const root = {
    setAttribute: (k, v) => (attrs[k] = v),
    removeAttribute: (k) => delete attrs[k],
  };
  applyMode(root, "dark");
  assert.strictEqual(attrs["data-theme"], "dark");
  applyMode(root, "system");
  assert.ok(!("data-theme" in attrs));
});

test("지금 어두운가: system 이면 기기 설정을 따름", () => {
  assert.strictEqual(isDarkMode("dark", false), true);
  assert.strictEqual(isDarkMode("light", true), false);
  assert.strictEqual(isDarkMode("system", true), true);
  assert.strictEqual(isDarkMode("system", false), false);
});

test("PC ☾/☀ 단추: 지금 보이는 것의 반대 모드를 저장 (두 단계)", () => {
  assert.strictEqual(toggledMode("system", false), "dark"); // 기기 밝음 → 어둡게
  assert.strictEqual(toggledMode("system", true), "light"); // 기기 어두움 → 밝게
  assert.strictEqual(toggledMode("dark", false), "light");
  assert.strictEqual(toggledMode("light", true), "dark");
});

test("PC 단추 모양 · 이름: 밝을 때 ☾ 어두운 화면으로, 어두울 때 ☀ 밝은 화면으로", () => {
  assert.deepStrictEqual(toggleLabel("light", true), { icon: "moon", label: "어두운 화면으로" });
  assert.deepStrictEqual(toggleLabel("system", true), { icon: "sun", label: "밝은 화면으로" });
});

// 깜빡임 방지: 페이지 5개 모두 head 에서 theme.js 를 css 보다 먼저, defer · async 없이 읽음 (인라인 스크립트 없이)
test("페이지 5개: head 에서 theme.js 를 css 보다 먼저 동기로, theme.css 를 페이지 css 보다 먼저", () => {
  const dir = path.join(__dirname, "../../main/resources/static");
  for (const [page, pageCss] of [
    ["index.html", "index.css"],
    ["about.html", "about.css"],
    ["login.html", "login.css"],
    ["mypage.html", "mypage.css"],
    ["privacy.html", "privacy.css"],
  ]) {
    const html = fs.readFileSync(path.join(dir, page), "utf8");
    const head = html.slice(0, html.indexOf("</head>"));
    const script = head.search(/<script src="\/theme\.js"><\/script>/);
    const firstCss = head.search(/<link rel="stylesheet"/);
    const themeCss = head.indexOf('href="/css/theme.css"');
    const ownCss = head.indexOf('href="/css/' + pageCss + '"');
    assert.ok(script >= 0, page + ": theme.js 가 head 에 없거나 defer · async 가 붙음");
    assert.ok(script < firstCss, page + ": theme.js 가 css 보다 뒤");
    assert.ok(themeCss >= 0 && themeCss < ownCss, page + ": theme.css 가 " + pageCss + " 보다 뒤");
  }
});

// 가짜 화면: 단추 · 3칸 · <html> · matchMedia
function fakePage(storage, systemDark) {
  function el(attrs) {
    const a = Object.assign({}, attrs);
    const handlers = [];
    return {
      attrs: a,
      getAttribute: (k) => (k in a ? a[k] : null),
      setAttribute: (k, v) => (a[k] = String(v)),
      removeAttribute: (k) => delete a[k],
      addEventListener: (type, fn) => handlers.push(fn),
      click() {
        handlers.forEach((fn) => fn.call(this, {}));
      },
    };
  }
  const root = el({});
  const toggle = el({});
  const modes = ["system", "light", "dark"].map((m) => el({ "data-mode": m }));
  const listeners = [];
  const query = { matches: systemDark, addEventListener: (t, fn) => listeners.push(fn) };
  const doc = {
    documentElement: root,
    querySelectorAll: (sel) => (sel === "[data-theme-toggle]" ? [toggle] : modes),
  };
  const win = {
    get localStorage() {
      if (storage === "throw") throw new Error("SecurityError");
      return storage;
    },
    matchMedia: () => query,
  };
  return { doc, win, root, toggle, modes, query, listeners };
}

test("PC 단추: 저장소가 막혀도 누를 때마다 다크 ↔ 라이트로 바뀜 (메모리 값 사용)", () => {
  for (const storage of ["throw", brokenStorage]) {
    const p = fakePage(storage, false);
    initThemeControls(p.doc, p.win);
    assert.strictEqual(p.toggle.attrs["aria-label"], "어두운 화면으로");
    p.toggle.click();
    assert.strictEqual(p.root.attrs["data-theme"], "dark");
    assert.strictEqual(p.toggle.attrs["aria-label"], "밝은 화면으로");
    p.toggle.click();
    assert.strictEqual(p.root.attrs["data-theme"], "light");
    assert.strictEqual(p.toggle.attrs["aria-label"], "어두운 화면으로");
  }
});

test("모바일 3칸: 고른 칸만 aria-pressed, 저장 · 적용, 기기 설정이면 속성 없음", () => {
  const s = memoryStorage({ theme: "dark" });
  const p = fakePage(s, false);
  initThemeControls(p.doc, p.win);
  assert.deepStrictEqual(p.modes.map((b) => b.attrs["aria-pressed"]), ["false", "false", "true"]);
  p.modes[1].click();
  assert.strictEqual(s.data.theme, "light");
  assert.strictEqual(p.root.attrs["data-theme"], "light");
  assert.deepStrictEqual(p.modes.map((b) => b.attrs["aria-pressed"]), ["false", "true", "false"]);
  p.modes[0].click();
  assert.strictEqual(s.data.theme, "system");
  assert.ok(!("data-theme" in p.root.attrs));
});

test("기기 설정을 따르는 중 기기가 어두워지면 PC 단추 이름도 바뀜", () => {
  const p = fakePage(memoryStorage({}), false);
  initThemeControls(p.doc, p.win);
  assert.strictEqual(p.toggle.attrs["aria-label"], "어두운 화면으로");
  p.query.matches = true;
  p.listeners.forEach((fn) => fn());
  assert.strictEqual(p.toggle.attrs["aria-label"], "밝은 화면으로");
});

test("☰ 화면 모드 3칸은 그림만 → 칸마다 이름(aria-label)이 있음", () => {
  const html = fs.readFileSync(path.join(__dirname, "../../main/resources/static/index.html"), "utf8");
  for (const [mode, label] of [
    ["system", "기기 설정"],
    ["light", "라이트"],
    ["dark", "다크"],
  ]) {
    const re = new RegExp('<button type="button" data-mode="' + mode + '"[^>]*aria-label="' + label + '"[^>]*><svg');
    assert.ok(re.test(html), mode + " 칸 이름 없음");
  }
});
