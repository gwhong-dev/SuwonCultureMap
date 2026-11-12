// 사진 없는 행사 기본 그림 시험 (확정 시안 P6) — default-poster.js · css/default-poster.css · theme.css 토큰
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  posterKind,
  posterLabel,
  posterPlace,
  defaultPosterHtml,
  posterOnError,
} = require("../../main/resources/static/default-poster.js");

const STATIC = path.join(__dirname, "../../main/resources/static");
const read = (name) => fs.readFileSync(path.join(STATIC, name), "utf8").replace(/\r\n/g, "\n");
const themeCss = read("css/theme.css");
const posterCss = read("css/default-poster.css");

test("분류 → 색 종류: 공연 · 전시 · 축제, 나머지(운영 DB 의 행사 · 기타 · 스포츠 · 빈 값)는 그 밖", () => {
  assert.strictEqual(posterKind("공연"), "perform");
  assert.strictEqual(posterKind("전시"), "exhibit");
  assert.strictEqual(posterKind(" 축제 "), "festival"); // 앞뒤 공백
  for (const c of ["행사", "기타", "스포츠", "교육", "", null, undefined, "toString"]) {
    assert.strictEqual(posterKind(c), "etc", String(c));
  }
});

test("라벨: 분류 이름 그대로, 비어 있으면 '행사'", () => {
  assert.strictEqual(posterLabel("스포츠"), "스포츠");
  assert.strictEqual(posterLabel(""), "행사");
  assert.strictEqual(posterLabel(null), "행사");
});

test("그림 HTML: 분류색 클래스 · 크기 클래스 · 라벨 · 이름 · 장소", () => {
  const html = defaultPosterHtml(
    { category: "공연", title: "가을 음악회", place: "수원SK아트리움" },
    "event-thumb"
  );
  assert.match(html, /^<span class="default-poster dp-perform event-thumb" aria-hidden="true">/);
  assert.match(html, /<span class="dp-label">공연<\/span>/);
  assert.match(html, /<span class="dp-title">가을 음악회<\/span>/);
  assert.match(html, /<span class="dp-place">수원SK아트리움<\/span>/);
});

test("장소: place 칸이 없으면 원문(info)의 장소 줄, 둘 다 없으면 장소 줄을 아예 안 그림", () => {
  assert.strictEqual(posterPlace({ info: "장소: 수원화성박물관\n시간: 10시" }), "수원화성박물관");
  const html = defaultPosterHtml({ category: "축제", title: "수원화성문화제", place: "  " });
  assert.ok(!html.includes("dp-place"), html);
  assert.ok(!defaultPosterHtml({ title: "x" }).includes("dp-place"));
  assert.match(defaultPosterHtml({ title: "x" }), /dp-etc[\s\S]*<span class="dp-label">행사<\/span>/);
});

test("제목 · 장소 · 클래스의 꺾쇠 · 따옴표는 글자로 (재단 제목의 <일월동행>)", () => {
  const html = defaultPosterHtml({ category: "<b>", title: "<일월동행>", place: '"광장"' }, 'x" onclick="y');
  assert.ok(html.includes("&lt;일월동행&gt;"));
  assert.ok(html.includes("&quot;광장&quot;"));
  assert.ok(html.includes("&lt;b&gt;"));
  assert.ok(!html.includes('onclick="y'));
});

test("빈 행사(null)도 깨지지 않음", () => {
  assert.match(defaultPosterHtml(null), /default-poster dp-etc/);
});

test("분류 라벨은 확정 시안 10.5~12px 굵게", () => {
  const label = /\.dp-label \{([^}]*)\}/.exec(posterCss)[1];
  assert.match(label, /font-size: clamp\(10\.5px, [^,]+, 12px\);/);
  assert.match(label, /font-weight: 700;/);
});

test("사진 없는 옛 처리(회색 칸 row-poster-text · 사진 칸 없앰 no-photo)가 다시 생기지 않음", () => {
  const js = read("js/index.js");
  const css = read("css/index.css");
  for (const old of ["row-poster-text", "no-photo"]) {
    assert.ok(!js.includes(old) && !css.includes(old), old);
  }
});

test("행사 이름은 최대 3줄 · 넘치면 줄임표, 세리프", () => {
  const title = /\.dp-title \{([^}]*)\}/.exec(posterCss)[1];
  assert.match(title, /-webkit-line-clamp: 3;/);
  assert.match(title, /-webkit-box-orient: vertical;/);
  assert.match(title, /overflow: hidden;/);
  assert.match(title, /font-family:[^;]*serif;/);
  assert.match(title, /color: var\(--text\);/);
});

test("사진을 못 불러오면 같은 크기 클래스의 기본 그림으로 바꿈 (src 가 빈 error 는 무시)", () => {
  global.document = {
    createElement: () => ({
      set innerHTML(html) {
        this.firstChild = { html };
      },
    }),
  };
  let listener;
  let replaced = null;
  const img = {
    className: "preview-poster",
    src: "https://x/a.jpg",
    getAttribute: () => img.src,
    addEventListener: (name, fn) => {
      assert.strictEqual(name, "error");
      listener = fn;
    },
    parentNode: { replaceChild: (next, old) => (replaced = { next, old }) },
  };
  posterOnError(img, { category: "전시", title: "근대 수원" });
  img.src = "";
  listener();
  assert.strictEqual(replaced, null, "빈 src 는 무시");
  img.src = "https://x/a.jpg";
  listener();
  assert.strictEqual(replaced.old, img);
  assert.match(replaced.next.html, /default-poster dp-exhibit preview-poster/);
  posterOnError(null, {}); // 사진이 없을 땐 그냥 지나감
  delete global.document;
});

// ===== 색: 토큰만 · 두 테마 · 대비 =====

function block(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const m = new RegExp("(?:^|\\n)\\s*" + escaped + "\\s*\\{").exec(themeCss);
  const open = themeCss.indexOf("{", m.index);
  const close = themeCss.indexOf("}", open);
  const decls = {};
  for (const d of themeCss.slice(open + 1, close).matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    decls[d[1]] = d[2].trim().split(/\s/)[0];
  }
  return decls;
}
const THEMES = {
  밝음: block(":root"),
  어두움: block(':root:not([data-theme="light"])'),
  "어두움(직접 고름)": block(':root[data-theme="dark"]'),
};

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

// css 의 .dp-종류 { --dp-bg: var(--토큰); --dp-ink: var(--토큰) } 읽기
function kindTokens(kind) {
  const body = new RegExp("\\.dp-" + kind + " \\{([^}]*)\\}").exec(posterCss)[1];
  return {
    bg: /--dp-bg: var\((--[\w-]+)\)/.exec(body)[1],
    ink: /--dp-ink: var\((--[\w-]+)\)/.exec(body)[1],
  };
}

test("분류색 = 확정 시안 토큰 (공연 파랑 · 전시 벽돌 · 축제 초록 · 그 밖 회색)", () => {
  assert.deepStrictEqual(kindTokens("perform"), { bg: "--blue-soft", ink: "--blue" });
  assert.deepStrictEqual(kindTokens("exhibit"), { bg: "--accent-soft", ink: "--accent-ink" });
  assert.deepStrictEqual(kindTokens("festival"), { bg: "--green-soft", ink: "--green" });
  assert.deepStrictEqual(kindTokens("etc"), { bg: "--surface", ink: "--sub" });
  assert.strictEqual(THEMES["밝음"]["--green-soft"], "#e7efe1");
  assert.strictEqual(THEMES["밝음"]["--green"], "#3f5f33");
});

test("축제 초록 다크 값: 미디어쿼리 · 직접 고름 두 곳이 같음", () => {
  for (const name of ["--green-soft", "--green"]) {
    assert.ok(THEMES["어두움"][name], name + " 다크 값 없음");
    assert.strictEqual(THEMES["어두움(직접 고름)"][name], THEMES["어두움"][name], name);
  }
});

test("글자 대비 4.5:1 이상 — 라벨 · 장소(분류 글자색) · 이름(--text), 4종류 × 두 테마", () => {
  for (const [theme, t] of Object.entries(THEMES)) {
    for (const kind of ["perform", "exhibit", "festival", "etc"]) {
      const { bg, ink } = kindTokens(kind);
      const label = contrast(t[ink], t[bg]);
      const title = contrast(t["--text"], t[bg]);
      assert.ok(label >= 4.5, `${theme} ${kind} 라벨 ${label.toFixed(2)}`);
      assert.ok(title >= 4.5, `${theme} ${kind} 이름 ${title.toFixed(2)}`);
    }
  }
});

test("default-poster.css 는 고정 색 없이 토큰만 · 다크 미디어쿼리 없음", () => {
  const noComments = posterCss.replace(/\/\*[\s\S]*?\*\//g, "");
  assert.deepStrictEqual(noComments.match(/#[0-9a-fA-F]{3,8}\b|:\s*(white|black)\b|rgba?\(/g) || [], []);
  assert.ok(!/prefers-color-scheme/.test(posterCss));
});

test("index.html 이 그림 함수 · css 를 읽음 (event-detail.js 뒤, index.js 앞)", () => {
  const html = read("index.html");
  const at = (s) => html.indexOf(s);
  assert.ok(at('<link rel="stylesheet" href="/css/default-poster.css" />') > at('href="/css/index.css"'));
  assert.ok(at('<script src="/default-poster.js"></script>') > at('<script src="/event-detail.js"></script>'));
  assert.ok(at('<script src="/default-poster.js"></script>') < at('<script src="/js/index.js"></script>'));
  assert.ok(html.includes('<div id="modalPoster" hidden></div>'));
});
