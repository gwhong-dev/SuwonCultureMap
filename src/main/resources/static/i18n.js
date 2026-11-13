// 화면 언어 (11/13 확정 시안 Q3 E안, docs/design/2026-11-13-todo-additions.md) — 한국어(기본) · English · 日本語 · 简体中文
// 1차 범위: 메뉴 · 버튼 · 안내 글(UI)만. 행사 제목 · 장소 · 설명은 원문(한국어) 그대로
// 글 묶음: static/i18n/{ko,en,ja,zh}.js (키 → 글, "{n}" 자리에 값). 이 파일보다 먼저 head 에서 동기로 읽음
// 저장: localStorage "lang" = "ko" | "en" | "ja" | "zh" (없음 = 아직 안 고름 → 한국어 + 첫 방문 안내 줄 조건)
// 적용: <html lang> + [data-i18n] 글 · [data-i18n-attr] 속성 (I18n.apply). JS 가 만드는 글은 I18n.t(키, 값)
// 언어를 바꾸면 저장 뒤 새로고침 (JS 가 그린 목록 · 카드 · 달력까지 한 번에 바뀌게)
// 시험: node --test src/test/js/i18n.test.js (CI 에서 실행)

var I18N_KEY = "lang";
var I18N_LANGS = ["ko", "en", "ja", "zh"];
var I18N_DEFAULT = "ko";
// 단추 · 목록에 쓰는 이름: native = 그 언어 글자, english = 영어 이름, code = 모바일 알약 단추
var I18N_NAMES = {
  ko: { native: "한국어", english: "Korean", code: "KO" },
  en: { native: "English", english: "English", code: "EN" },
  ja: { native: "日本語", english: "Japanese", code: "JA" },
  zh: { native: "简体中文", english: "Chinese", code: "中文" },
};
// <html lang> 값 (중국어는 간체 표시라 글꼴 · 읽기 프로그램이 맞는 글자를 고르게)
var I18N_HTML_LANG = { ko: "ko", en: "en", ja: "ja", zh: "zh-Hans" };

function normalizeLang(value) {
  return I18N_LANGS.indexOf(value) >= 0 ? value : null;
}

// 저장된 언어 (없거나 틀린 값 · 저장소 막힘 = null)
function readLang(storage) {
  try {
    return normalizeLang(storage ? storage.getItem(I18N_KEY) : null);
  } catch (e) {
    return null;
  }
}

function saveLang(storage, lang) {
  var l = normalizeLang(lang) || I18N_DEFAULT;
  try {
    if (storage) storage.setItem(I18N_KEY, l);
  } catch (e) {
    // 저장 못 해도 이번 화면에는 적용됨
  }
  return l;
}

// 브라우저 언어 목록(navigator.languages)에서 처음으로 맞는 언어. "zh-TW" 같은 번체도 简体中文 으로 봄 (번체 묶음은 없음)
function browserLang(languages) {
  var list = languages || [];
  for (var i = 0; i < list.length; i++) {
    var base = String(list[i] || "").toLowerCase().split("-")[0];
    if (normalizeLang(base)) return base;
  }
  return null;
}

// 첫 방문 안내 줄을 띄울 언어: 아직 안 골랐고, 브라우저 언어가 en · ja · zh 일 때만 (ko 면 안내 없음)
function offerLang(saved, languages) {
  if (saved) return null;
  var lang = browserLang(languages);
  return lang && lang !== I18N_DEFAULT ? lang : null;
}

// "{n}개" + { n: 3 } → "3개". 값이 없는 자리는 그대로 둠 (빠진 값을 찾기 쉽게)
function formatMessage(template, vars) {
  if (typeof template !== "string") return template;
  return template.replace(/\{(\w+)\}/g, function (all, name) {
    return vars && vars[name] !== undefined && vars[name] !== null ? String(vars[name]) : all;
  });
}

// 글 묶음 모음: 브라우저 = i18n/*.js 가 채운 window.I18N_MESSAGES, node = require
function loadMessages() {
  if (typeof module !== "undefined" && typeof require === "function") {
    return {
      ko: require("./i18n/ko.js"),
      en: require("./i18n/en.js"),
      ja: require("./i18n/ja.js"),
      zh: require("./i18n/zh.js"),
    };
  }
  return (typeof window !== "undefined" && window.I18N_MESSAGES) || {};
}

function createI18n(messages, lang) {
  var current = normalizeLang(lang) || I18N_DEFAULT;

  // 지금 언어 → 한국어 → 키 순서로 찾음 (번역이 빠져도 화면이 비지 않게)
  function raw(key) {
    var own = messages[current] && messages[current][key];
    if (own !== undefined && own !== "") return own;
    var ko = messages[I18N_DEFAULT] && messages[I18N_DEFAULT][key];
    return ko !== undefined ? ko : key;
  }
  function t(key, vars) {
    return formatMessage(raw(key), vars);
  }
  // 다른 언어의 글 (첫 방문 안내 줄은 아직 한국어 화면에서 그 브라우저 언어로 씀)
  function tIn(lang, key, vars) {
    var own = messages[lang] && messages[lang][key];
    return formatMessage(own !== undefined && own !== "" ? own : raw(key), vars);
  }
  function list(key) {
    var value = raw(key);
    return Array.isArray(value) ? value : [];
  }

  // ===== 날짜 표시 (예: 11월 13일 (금) · Fri, Nov 13 · 11月13日（金）) =====
  function weekday(index) {
    return list("date.weekdays")[index] || "";
  }
  function dateVars(month, day, weekdayIndex, year) {
    return {
      y: year,
      m: month,
      d: day,
      mon: list("date.months")[month - 1] || month,
      w: weekdayIndex === undefined ? "" : weekday(weekdayIndex),
    };
  }
  function weekdayOfIso(iso) {
    var p = String(iso).split("-");
    return new Date(Date.UTC(+p[0], +p[1] - 1, +p[2])).getUTCDay();
  }

  var api = {
    LANGS: I18N_LANGS,
    NAMES: I18N_NAMES,
    t: t,
    tIn: tIn,
    list: list,
    lang: function () {
      return current;
    },
    htmlLang: function () {
      return I18N_HTML_LANG[current];
    },
    weekday: weekday,
    // "11월" · "Nov"
    month: function (month) {
      return t("date.month", dateVars(month));
    },
    // "2026년 11월" · "Nov 2026"
    yearMonth: function (year, month) {
      return t("date.yearMonth", dateVars(month, undefined, undefined, year));
    },
    year: function (year) {
      return t("date.year", { y: year });
    },
    // "11월 13일" · "Nov 13"
    monthDay: function (month, day) {
      return t("date.monthDay", dateVars(month, day));
    },
    // ISO "2026-11-13" → "11월 13일 (금)" · "Fri, Nov 13"
    dayTitle: function (iso) {
      var p = String(iso).split("-");
      return t("date.dayTitle", dateVars(+p[1], +p[2], weekdayOfIso(iso)));
    },
    // 행사 분류 · 구 이름처럼 데이터에서 온 짧은 이름: 아는 것만 번역, 모르는 이름은 원문
    name: function (group, value) {
      var key = group + "." + value;
      var found = raw(key);
      return found === key ? value : found;
    },
    // 테스트용: 언어만 바꿈 (저장 · 새로고침 없음)
    use: function (lang) {
      current = normalizeLang(lang) || I18N_DEFAULT;
    },
  };
  return api;
}

// 화면 글 바꾸기: data-i18n="키" → 글자, data-i18n-attr="aria-label:키;title:키" → 속성
function applyI18n(root, i18n) {
  var nodes = root.querySelectorAll("[data-i18n]");
  for (var i = 0; i < nodes.length; i++) {
    nodes[i].textContent = i18n.t(nodes[i].getAttribute("data-i18n"));
  }
  var attrs = root.querySelectorAll("[data-i18n-attr]");
  for (var j = 0; j < attrs.length; j++) {
    var pairs = attrs[j].getAttribute("data-i18n-attr").split(";");
    for (var k = 0; k < pairs.length; k++) {
      var at = pairs[k].indexOf(":");
      if (at < 0) continue;
      attrs[j].setAttribute(pairs[k].slice(0, at).trim(), i18n.t(pairs[k].slice(at + 1).trim()));
    }
  }
}

if (typeof module !== "undefined") {
  module.exports = {
    I18N_LANGS: I18N_LANGS,
    I18N_NAMES: I18N_NAMES,
    normalizeLang: normalizeLang,
    readLang: readLang,
    saveLang: saveLang,
    browserLang: browserLang,
    offerLang: offerLang,
    formatMessage: formatMessage,
    createI18n: createI18n,
    applyI18n: applyI18n,
    loadMessages: loadMessages,
  };
}

// 전역 I18n: 브라우저는 저장된 언어, node(시험)는 한국어. 다른 파일은 I18n.t(...) 로 씀
var I18n = (function () {
  var storage = null;
  try {
    storage = typeof window !== "undefined" ? window.localStorage : null;
  } catch (e) {
    storage = null;
  }
  var api = createI18n(loadMessages(), readLang(storage));
  var applied = false;
  api.saved = function () {
    return readLang(storage);
  };
  // 고른 언어 저장. reload = true 면 새로고침해 JS 가 그린 글까지 바꿈
  api.set = function (lang, reload) {
    saveLang(storage, lang);
    if (reload && typeof location !== "undefined") location.reload();
  };
  // 첫 방문 안내 줄을 띄울 언어 (없으면 null)
  api.offer = function () {
    return offerLang(readLang(storage), typeof navigator !== "undefined" ? navigator.languages || [navigator.language] : []);
  };
  api.apply = function (root) {
    if (typeof document === "undefined") return;
    var doc = root || document;
    applyI18n(doc, api);
    if (doc === document) applied = true;
  };
  if (typeof document !== "undefined") {
    document.documentElement.setAttribute("lang", api.htmlLang());
    // 페이지 JS 가 먼저 apply 를 부르지 않았어도(about 등) 본문을 다 읽으면 한 번 적용
    document.addEventListener("DOMContentLoaded", function () {
      if (!applied) api.apply(document);
    });
  }
  return api;
})();

if (typeof module !== "undefined") module.exports.I18n = I18n;
