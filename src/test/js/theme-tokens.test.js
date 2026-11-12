// 색 토큰 · 테마 고르기 규칙 시험 (css/theme.css · 각 페이지 css)
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const CSS_DIR = path.join(__dirname, "../../main/resources/static/css");
const read = (name) => fs.readFileSync(path.join(CSS_DIR, name), "utf8").replace(/\r\n/g, "\n");
const css = read("theme.css"); // 토큰
const indexCss = read("index.css");
const PAGE_CSS = ["index.css", "about.css", "login.css", "mypage.css", "privacy.css"];

// 고정 색 찾기: #색 · white · black · 밝은 rgba (검정 그림자 rgba(0, 0, 0, …)만 허용), 주석은 뺌
function fixedColors(text) {
  const noComments = text.replace(/\/\*[\s\S]*?\*\//g, "");
  return noComments.match(/#[0-9a-fA-F]{3,8}\b|:\s*(white|black)\b|rgba?\((?!0, 0, 0,)[^)]*\)/g) || [];
}

// 선택자 바로 뒤 { ... } 안의 "--이름: 값" 들을 읽음 (줄 앞 공백 · 선택자 뒤 공백은 상관없음)
function block(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const m = new RegExp("(?:^|\\n)\\s*" + escaped + "\\s*\\{").exec(css);
  assert.ok(m, selector + " 블록이 없음");
  const open = css.indexOf("{", m.index);
  const close = css.indexOf("}", open);
  const decls = {};
  for (const d of css.slice(open + 1, close).matchAll(/(--[\w-]+|color-scheme)\s*:\s*([^;]+);/g)) {
    decls[d[1]] = d[2].trim();
  }
  return decls;
}

const light = block(":root");
const darkMedia = block(':root:not([data-theme="light"])');
const darkPicked = block(':root[data-theme="dark"]');

test("어두운 값: 기기 설정(미디어쿼리)과 직접 고름(data-theme=dark)이 같음", () => {
  assert.deepStrictEqual(darkPicked, darkMedia);
});

test("어두운 값의 토큰은 모두 밝은 값에도 있음", () => {
  for (const name of Object.keys(darkMedia)) {
    assert.ok(name in light, name + " 의 밝은 값이 없음");
  }
});

test("상단 바 · 로고 토큰 (확정 시안 B: 종이색 바)", () => {
  assert.strictEqual(light["--bar"], "#f6f1e7");
  assert.strictEqual(darkMedia["--bar"], "#1e1c1a");
  assert.strictEqual(light["--bar-line"], "#e3dac8");
  assert.strictEqual(darkMedia["--bar-line"], "#35322e");
  assert.strictEqual(light["--brand-sub"], "#b4472a");
  assert.strictEqual(darkMedia["--brand-sub"], "#e07a57");
  assert.strictEqual(light["--seal-frame"], "transparent");
  assert.strictEqual(darkMedia["--seal-frame"], "#f6f1e7");
});

// WCAG 명암비 (#rrggbb 두 색)
function contrast(a, b) {
  const lum = (hex) => {
    const [r, g, bl] = [1, 3, 5].map((i) => {
      const v = parseInt(hex.slice(i, i + 2), 16) / 255;
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

test("마이 페이지 '연동됨' 글자(--success)는 카드 바탕(--bg) 위 4.5:1 이상 (두 테마)", () => {
  assert.ok(contrast(light["--success"], light["--bg"]) >= 4.5, "밝은 테마");
  assert.ok(contrast(darkMedia["--success"], darkMedia["--bg"]) >= 4.5, "어두운 테마");
});

test("어두운 미디어쿼리는 theme.css 토큰 블록 하나뿐 (data-theme=light 로 끌 수 있게)", () => {
  const count = (css.match(/@media \(prefers-color-scheme: dark\)/g) || []).length;
  assert.strictEqual(count, 1);
  for (const name of PAGE_CSS) {
    assert.ok(!/prefers-color-scheme/.test(read(name)), name + " 에 따로 둔 다크 미디어쿼리");
  }
});

test("페이지 css 는 색 토큰을 다시 정하지 않음 (theme.css 한 곳)", () => {
  for (const name of PAGE_CSS) {
    assert.ok(!/--(bg|surface|line|text|sub|accent)\s*:/.test(read(name)), name + " 에 색 토큰을 다시 정함");
  }
});

test("index.css PC 부분에 고정 색 없음 (브랜드 버튼 · 찜 별 · 사진 위 × 만 예외)", () => {
  const pc = indexCss
    .slice(0, indexCss.indexOf("@media (max-width: 1024px)"))
    .replace(/\/\*[\s\S]*?\*\//g, "") // 주석 빼기
    .replace(/\.login-btn\.(kakao|google|naver) \{[^}]*\}/g, "") // 소셜 로그인 버튼은 각 회사 색
    .replace(/\.route-pop-icon\.(kakao|naver) \{[^}]*\}/g, "") // PC 길찾기 창의 지도 앱 표시도 각 회사 색
    .replace(/\.favorite-(badge|btn)[^{]*\{[^}]*\}/g, "") // 찜 별 주황 (두 테마 같은 색)
    .replace(/\.modal-close \{[^}]*\}/g, ""); // 사진 위 흰 ×
  assert.deepStrictEqual(fixedColors(pc), []);
});

test("about · login · mypage · privacy 도 토큰만 (소셜 로그인 · 연결 단추의 회사 색만 예외)", () => {
  for (const name of ["about.css", "login.css", "mypage.css", "privacy.css"]) {
    const rest = read(name)
      .replace(/\/\*[\s\S]*?\*\//g, "")
      .replace(/\.(social-btn|link-btn)\.(kakao|google|naver) \{[^}]*\}/g, "");
    assert.deepStrictEqual(fixedColors(rest), [], name);
  }
});
