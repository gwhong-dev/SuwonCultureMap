// 화면 언어 시험 (i18n.js · static/i18n/*.js · lang-picker.js) — 11/13 확정 시안 Q3
// ① 4개 언어 글 묶음의 키가 모두 같고 빈 값이 없는지 ② 코드가 쓰는 키가 다 있는지 ③ 언어 고르기 · 날짜 규칙
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const STATIC = path.join(__dirname, "../../main/resources/static");
const {
  I18N_LANGS,
  readLang,
  saveLang,
  browserLang,
  offerLang,
  formatMessage,
  createI18n,
  applyI18n,
  loadMessages,
  I18n,
} = require(path.join(STATIC, "i18n.js"));
const { langStep } = require(path.join(STATIC, "lang-picker.js"));

const messages = loadMessages();
const placeholders = (value) =>
  (Array.isArray(value) ? value.join(" ") : String(value)).match(/\{\w+\}/g)?.sort().join(",") || "";

test("글 묶음: ko · en · ja · zh 4개가 다 있고 키가 모두 같음", () => {
  assert.deepStrictEqual(I18N_LANGS, ["ko", "en", "ja", "zh"]);
  const koKeys = Object.keys(messages.ko).sort();
  assert.ok(koKeys.length > 200, "키가 너무 적음: " + koKeys.length);
  for (const lang of ["en", "ja", "zh"]) {
    const keys = Object.keys(messages[lang]).sort();
    const missing = koKeys.filter((k) => !keys.includes(k));
    const extra = keys.filter((k) => !koKeys.includes(k));
    assert.deepStrictEqual(missing, [], lang + " 에 빠진 키");
    assert.deepStrictEqual(extra, [], lang + " 에만 있는 키");
  }
});

test("글 묶음: 빈 값이 없고, 배열(요일 7 · 달 12) 길이 · {자리} 이름이 언어마다 같음", () => {
  for (const lang of I18N_LANGS) {
    for (const [key, value] of Object.entries(messages[lang])) {
      if (Array.isArray(value)) {
        assert.strictEqual(value.length, messages.ko[key].length, lang + " " + key + " 길이");
        value.forEach((v, i) => assert.ok(typeof v === "string" && v.trim(), lang + " " + key + "[" + i + "] 빈 값"));
      } else {
        assert.ok(typeof value === "string" && value.trim() !== "", lang + " " + key + " 빈 값");
      }
      // 날짜 틀은 언어마다 쓰는 자리가 다름 ({m} 숫자 달 · {mon} 달 이름) → 날짜 시험이 따로 확인
      if (!key.startsWith("date.")) {
        assert.strictEqual(placeholders(value), placeholders(messages.ko[key]), lang + " " + key + " 의 {자리}");
      }
    }
  }
  assert.strictEqual(messages.ko["date.weekdays"].length, 7);
  assert.strictEqual(messages.ko["date.months"].length, 12);
});

test("코드 · HTML 이 쓰는 키가 글 묶음에 모두 있음", () => {
  const files = fs
    .readdirSync(STATIC)
    .filter((f) => /\.(js|html)$/.test(f))
    .concat(fs.readdirSync(path.join(STATIC, "js")).map((f) => "js/" + f));
  const prefixes = new Set(Object.keys(messages.ko).map((k) => k.split(".")[0]));
  const used = new Set();
  for (const file of files) {
    const src = fs.readFileSync(path.join(STATIC, file), "utf8");
    // 글 묶음 키와 접두어가 같은 "접두어.이름" 문자열 전부 (I18n.t · i18nApi.t · 삼항 · STATUS_KEYS 같은 표 · tIn · themeText)
    // 받는 이름 · 호출 모양과 상관없이 잡음 → 키 오타가 나면 여기서 걸림 (#252 검증 ⚠️)
    for (const m of src.matchAll(/"([a-z][a-zA-Z]*)\.([a-zA-Z][\w]*)"/g)) {
      if (prefixes.has(m[1])) used.add(m[1] + "." + m[2]);
    }
    for (const m of src.matchAll(/data-i18n="([\w.]+)"/g)) used.add(m[1]);
    for (const m of src.matchAll(/data-i18n-attr="([^"]+)"/g)) {
      m[1].split(";").forEach((pair) => used.add(pair.split(":")[1].trim()));
    }
    for (const m of src.matchAll(/themeText\("([\w.]+)"/g)) used.add(m[1]);
  }
  // 주석 속 예시(data-i18n="키") · 파일 이름("login.html") 은 키가 아님
  const missing = [...used].filter((k) => k.includes(".") && !/\.(html|js|css|json|png|svg)$/.test(k) && !(k in messages.ko));
  assert.deepStrictEqual(missing, []);
  assert.ok(used.size > 200, "찾은 키가 너무 적음 (정규식 확인): " + used.size);
  // 전에 놓치던 모양도 잡히는지 (소문자 받는 이름 · 괄호 든 삼항 · 3단 삼항 첫 키 · 변수 표)
  for (const k of ["sched.daily", "detail.viewInstagram", "list.countFav", "strip.swipe", "status.open"]) assert.ok(used.has(k), k);
});

test("브라우저 언어: navigator.languages 에서 처음 맞는 언어, 번체 zh-TW 도 zh", () => {
  assert.strictEqual(browserLang(["ja-JP", "en-US"]), "ja");
  assert.strictEqual(browserLang(["fr-FR", "en-GB"]), "en");
  assert.strictEqual(browserLang(["zh-TW"]), "zh");
  assert.strictEqual(browserLang(["zh-Hans-CN"]), "zh");
  assert.strictEqual(browserLang(["ko-KR", "en-US"]), "ko");
  assert.strictEqual(browserLang(["fr", "de"]), null);
  assert.strictEqual(browserLang(undefined), null);
});

test("첫 방문 안내 줄: 아직 안 골랐고 브라우저 언어가 en · ja · zh 일 때만", () => {
  assert.strictEqual(offerLang(null, ["ja"]), "ja");
  assert.strictEqual(offerLang(null, ["en-US"]), "en");
  assert.strictEqual(offerLang(null, ["zh-CN"]), "zh");
  assert.strictEqual(offerLang(null, ["ko-KR", "en"]), null); // 한국어가 먼저면 안내 없음
  assert.strictEqual(offerLang(null, ["fr"]), null);
  assert.strictEqual(offerLang("ko", ["ja"]), null); // 한 번 고르면(한국어 그대로 · × 포함) 다시 안 뜸
  assert.strictEqual(offerLang("en", ["en"]), null);
});

test("저장소가 막힌 브라우저: 주소 ?lang= 으로 그 페이지 언어를 정함", () => {
  const { urlLang, urlWithLang } = require(path.join(STATIC, "i18n.js"));
  assert.strictEqual(urlLang("?lang=ja"), "ja");
  assert.strictEqual(urlLang("?event=3&lang=zh"), "zh");
  assert.strictEqual(urlLang("?lang=fr"), null);
  assert.strictEqual(urlLang("?slang=en"), null);
  assert.strictEqual(urlLang(""), null);
  assert.strictEqual(urlWithLang("https://a.com/", "en"), "https://a.com/?lang=en");
  assert.strictEqual(urlWithLang("https://a.com/?event=3", "ja"), "https://a.com/?event=3&lang=ja");
  assert.strictEqual(urlWithLang("https://a.com/?lang=ja&event=3#x", "zh"), "https://a.com/?event=3&lang=zh#x");
  assert.strictEqual(urlWithLang("https://a.com/?event=3&lang=ja", "en"), "https://a.com/?event=3&lang=en");
});

test("저장 · 읽기: 모르는 값은 null, 저장소가 막혀도 멈추지 않음", () => {
  const box = {};
  const storage = { getItem: (k) => (k in box ? box[k] : null), setItem: (k, v) => (box[k] = v) };
  assert.strictEqual(readLang(storage), null);
  assert.strictEqual(saveLang(storage, "ja"), "ja");
  assert.strictEqual(readLang(storage), "ja");
  box.lang = "fr";
  assert.strictEqual(readLang(storage), null);
  assert.strictEqual(saveLang(storage, "fr"), "ko"); // 모르는 언어 → 기본 한국어
  const broken = {
    getItem() {
      throw new Error("blocked");
    },
    setItem() {
      throw new Error("blocked");
    },
  };
  assert.strictEqual(readLang(broken), null);
  assert.strictEqual(saveLang(broken, "en"), "en");
  assert.strictEqual(readLang(null), null);
});

test("글 채우기: {자리} 값을 넣고, 값이 없는 자리는 그대로 둠", () => {
  assert.strictEqual(formatMessage("행사 {n}개", { n: 3 }), "행사 3개");
  assert.strictEqual(formatMessage("{a}·{b}", { a: 1 }), "1·{b}");
  assert.strictEqual(formatMessage("0도 값", { n: 0 }), "0도 값");
  assert.strictEqual(formatMessage("{n}", { n: 0 }), "0");
});

test("찾는 순서: 그 언어 → 한국어 → 키 (번역이 빠져도 화면이 비지 않음)", () => {
  const i18n = createI18n({ ko: { a: "가", b: "나" }, en: { a: "A", b: "" } }, "en");
  assert.strictEqual(i18n.t("a"), "A");
  assert.strictEqual(i18n.t("b"), "나");
  assert.strictEqual(i18n.t("none.key"), "none.key");
  assert.strictEqual(i18n.lang(), "en");
  assert.strictEqual(createI18n({ ko: {} }, "fr").lang(), "ko");
});

test("날짜: 언어마다 '11월 13일 (금)' 모양", () => {
  const want = {
    ko: ["11월 13일 (금)", "11월", "2026년 11월", "2026년", "11월 13일"],
    en: ["Fri, Nov 13", "Nov", "Nov 2026", "2026", "Nov 13"],
    ja: ["11月13日（金）", "11月", "2026年11月", "2026年", "11月13日"],
    zh: ["11月13日（周五）", "11月", "2026年11月", "2026年", "11月13日"],
  };
  for (const lang of I18N_LANGS) {
    const i18n = createI18n(messages, lang);
    assert.deepStrictEqual(
      [i18n.dayTitle("2026-11-13"), i18n.month(11), i18n.yearMonth(2026, 11), i18n.year(2026), i18n.monthDay(11, 13)],
      want[lang],
      lang,
    );
    assert.strictEqual(i18n.weekday(0), messages[lang]["date.weekdays"][0]);
  }
});

test("분류 · 구 이름: 아는 이름만 번역, 모르는 이름은 원문", () => {
  const en = createI18n(messages, "en");
  assert.strictEqual(en.name("cat", "공연"), "Performances");
  assert.strictEqual(en.name("cat", "새분류"), "새분류");
  assert.strictEqual(en.name("district", "팔달구"), "Paldal-gu");
  assert.strictEqual(en.name("districtShort", "팔달구"), "Paldal");
  assert.strictEqual(createI18n(messages, "ko").name("districtShort", "영통구"), "영통");
});

test("다른 언어 글 꺼내기 (첫 방문 안내 줄은 한국어 화면에서 그 브라우저 언어로)", () => {
  const ko = createI18n(messages, "ko");
  assert.strictEqual(ko.tIn("ja", "lang.offerText"), "このページは日本語でもご覧いただけます。");
  assert.strictEqual(ko.tIn("en", "lang.offerButton"), "View in English");
  assert.strictEqual(ko.tIn("zh", "lang.offerButton"), "查看简体中文");
});

test("HTML 적용: data-i18n 은 글자, data-i18n-attr 은 속성", () => {
  const makeEl = (attrs) => ({
    attrs: { ...attrs },
    textContent: "",
    getAttribute(name) {
      return this.attrs[name];
    },
    setAttribute(name, value) {
      this.attrs[name] = value;
    },
  });
  const text = makeEl({ "data-i18n": "nav.login" });
  const attr = makeEl({ "data-i18n-attr": "aria-label:common.close; title:menu.label" });
  const root = {
    querySelectorAll(sel) {
      return sel === "[data-i18n]" ? [text] : [attr];
    },
  };
  applyI18n(root, createI18n(messages, "ja"));
  assert.strictEqual(text.textContent, "ログイン");
  assert.strictEqual(attr.attrs["aria-label"], "閉じる");
  assert.strictEqual(attr.attrs.title, "メニュー");
});

test("화면 모듈도 고른 언어를 따름 (상태 배지 · D-day · 요일 요약)", () => {
  const schedule = require(path.join(STATIC, "schedule-format.js"));
  const now = Date.UTC(2026, 10, 13, 3); // 한국 11/13 12시
  const event = { startDate: "2026-11-01", endDate: "2026-11-16" };
  assert.strictEqual(schedule.statusLabel("open"), "운영중");
  assert.strictEqual(schedule.ddayText(event, now), "종료까지 D-3");
  try {
    I18n.use("en");
    assert.strictEqual(schedule.statusLabel("open"), "Open");
    assert.strictEqual(schedule.ddayText(event, now), "Ends in D-3");
    I18n.use("ja");
    assert.strictEqual(schedule.ddayText({ startDate: "2026-11-13", endDate: "2026-11-13" }, now), "本日終了");
  } finally {
    I18n.use("ko");
  }
});

test("언어 목록 키보드: 위아래 화살표 · Home · End, 끝에서 멈춤", () => {
  assert.strictEqual(langStep(0, "ArrowDown", 4), 1);
  assert.strictEqual(langStep(3, "ArrowDown", 4), 3);
  assert.strictEqual(langStep(0, "ArrowUp", 4), 0);
  assert.strictEqual(langStep(2, "Home", 4), 0);
  assert.strictEqual(langStep(1, "End", 4), 3);
  assert.strictEqual(langStep(1, "Enter", 4), null);
  assert.strictEqual(langStep(0, "ArrowDown", 0), null);
});

test("HTML: 언어 단추 · 판 자리와 글 묶음 · i18n.js 를 theme.js 보다 먼저 읽음", () => {
  const index = fs.readFileSync(path.join(STATIC, "index.html"), "utf8");
  for (const id of ["langBtn", "langPop", "homeLangBtn", "menuLangBtn"]) assert.ok(index.includes('id="' + id + '"'), id);
  for (const page of ["index.html", "login.html", "mypage.html"]) {
    const html = fs.readFileSync(path.join(STATIC, page), "utf8");
    const order = ["/i18n/ko.js", "/i18n/en.js", "/i18n/ja.js", "/i18n/zh.js", "/i18n.js", "/theme.js"].map((src) =>
      html.indexOf('<script src="' + src + '"'),
    );
    order.forEach((at, i) => assert.ok(at > 0, page + " 에 " + i + "번 스크립트 없음"));
    assert.deepStrictEqual([...order].sort((a, b) => a - b), order, page + " 스크립트 순서");
  }
  // ☾ 바로 왼쪽 (LangE)
  assert.ok(index.indexOf('id="langBtn"') < index.indexOf('id="themeToggleBtn"'));
  assert.ok(index.indexOf('id="homeLangBtn"') < index.indexOf('id="homeThemeBtn"'));
});
