// 색 토큰 · 테마 고르기 규칙 시험 (css/index.css)
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const css = fs.readFileSync(path.join(__dirname, "../../main/resources/static/css/index.css"), "utf8").replace(/\r\n/g, "\n");

// 선택자 바로 뒤 { ... } 안의 "--이름: 값" 들을 읽음 (줄 앞 공백 · 선택자 뒤 공백은 상관없음)
function block(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const m = new RegExp("(?:^|\\n)\\s*" + escaped + "\\s*\\{").exec(css);
  assert.ok(m, selector + " 블록이 없음");
  const open = css.indexOf("{", m.index);
  const close = css.indexOf("}", open);
  const decls = {};
  for (const m of css.slice(open + 1, close).matchAll(/(--[\w-]+|color-scheme)\s*:\s*([^;]+);/g)) {
    decls[m[1]] = m[2].trim();
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

test("어두운 미디어쿼리는 토큰 블록 하나뿐 (data-theme=light 로 끌 수 있게)", () => {
  const count = (css.match(/@media \(prefers-color-scheme: dark\)/g) || []).length;
  assert.strictEqual(count, 1);
});

test("PC 부분에 고정 색 없음 (브랜드 버튼 · 찜 별 · 사진 위 × 만 예외)", () => {
  const darkEnd = css.indexOf("}", css.indexOf(':root[data-theme="dark"]'));
  const pcEnd = css.indexOf("@media (max-width: 1024px)");
  const pc = css
    .slice(darkEnd, pcEnd)
    .replace(/\/\*[\s\S]*?\*\//g, "") // 주석 빼기
    .replace(/\.login-btn\.(kakao|google|naver) \{[^}]*\}/g, "") // 소셜 로그인 버튼은 각 회사 색
    .replace(/\.favorite-(badge|btn)[^{]*\{[^}]*\}/g, "") // 찜 별 주황 (두 테마 같은 색)
    .replace(/\.modal-close \{[^}]*\}/g, ""); // 사진 위 흰 ×
  // #색 · white · black · 밝은 rgba (검정 그림자 rgba(0, 0, 0, …)만 허용)
  const left = pc.match(/#[0-9a-fA-F]{3,8}\b|:\s*(white|black)\b|rgba?\((?!0, 0, 0,)[^)]*\)/g) || [];
  assert.deepStrictEqual(left, []);
});
