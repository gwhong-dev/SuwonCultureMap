// 화면 모드 (기기 설정 · 라이트 · 다크) — 모든 페이지 head 에서 css 보다 먼저, 동기로 읽음 (인라인 스크립트는 CSP 로 막힘)
// 저장: localStorage "theme" = "system" | "light" | "dark" (없거나 틀린 값 = system)
// 적용: <html data-theme="light|dark"> — system 이면 속성을 지워 css/theme.css 의 prefers-color-scheme 를 따름
// 단추: PC 상단 바 ☾/☀ (두 단계, [data-theme-toggle], 그림은 css) · 모바일 ☰ 메뉴 "화면 모드" 3칸 ([data-theme-mode] 안의 [data-mode])
// 시험: node --test src/test/js/ (CI 에서 실행)

var THEME_KEY = "theme";
var THEME_MODES = ["system", "light", "dark"];

// 저장값 → 모드 (모르는 값은 system)
function normalizeMode(value) {
  return THEME_MODES.indexOf(value) >= 0 ? value : "system";
}

// 사파리 개인 정보 보호 모드 · 차단된 저장소에서도 멈추지 않게 try/catch
function readMode(storage) {
  try {
    return normalizeMode(storage ? storage.getItem(THEME_KEY) : null);
  } catch (e) {
    return "system";
  }
}

function saveMode(storage, mode) {
  var m = normalizeMode(mode);
  try {
    if (storage) storage.setItem(THEME_KEY, m);
  } catch (e) {
    // 저장 못 해도 이번 화면에는 적용됨
  }
  return m;
}

// <html data-theme> 값: system 이면 null (속성 없음)
function themeAttr(mode) {
  var m = normalizeMode(mode);
  return m === "system" ? null : m;
}

// 지금 실제로 어두운가 (system 이면 기기 설정)
function isDarkMode(mode, systemDark) {
  var m = normalizeMode(mode);
  return m === "dark" || (m === "system" && !!systemDark);
}

// PC ☾/☀ 단추: 지금 보이는 것의 반대 모드
function toggledMode(mode, systemDark) {
  return isDarkMode(mode, systemDark) ? "light" : "dark";
}

// 화면 언어 (i18n.js) 가 있는 페이지는 그 언어로, 없는 페이지(about · privacy) · node 시험은 한국어
function themeText(key, ko) {
  return typeof I18n !== "undefined" ? I18n.t(key) : ko;
}

// PC 단추 모양: 밝을 때 ☾ "어두운 화면으로", 어두울 때 ☀ "밝은 화면으로"
function toggleLabel(mode, systemDark) {
  return isDarkMode(mode, systemDark)
    ? { icon: "sun", label: themeText("theme.toLight", "밝은 화면으로") }
    : { icon: "moon", label: themeText("theme.toDark", "어두운 화면으로") };
}

function applyMode(root, mode) {
  var attr = themeAttr(mode);
  if (attr) root.setAttribute("data-theme", attr);
  else root.removeAttribute("data-theme");
}

function safeStorage(win) {
  try {
    return win.localStorage;
  } catch (e) {
    return null;
  }
}

function systemDarkQuery(win) {
  try {
    return win.matchMedia ? win.matchMedia("(prefers-color-scheme: dark)") : null;
  } catch (e) {
    return null;
  }
}

// 단추 연결 (본문이 그려진 뒤). 단추가 없는 페이지(about 등)는 아무것도 안 함
// 고른 모드는 메모리(current)에 들고 있음 → 저장소가 막혀 저장이 안 돼도 단추는 양쪽으로 바뀜
// ☾/☀ 그림은 css 가 테마에 맞춰 보이고(--icon-moon · --icon-sun), 여기서는 이름 · aria-pressed 만 바꿈
function initThemeControls(doc, win) {
  var storage = safeStorage(win);
  var query = systemDarkQuery(win);
  var current = readMode(storage);
  var toggles = doc.querySelectorAll("[data-theme-toggle]");
  var modeButtons = doc.querySelectorAll("[data-theme-mode] [data-mode]");

  function render() {
    var look = toggleLabel(current, query && query.matches);
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].setAttribute("aria-label", look.label);
      toggles[i].setAttribute("title", look.label);
    }
    for (var j = 0; j < modeButtons.length; j++) {
      modeButtons[j].setAttribute("aria-pressed", String(modeButtons[j].getAttribute("data-mode") === current));
    }
  }
  function choose(mode) {
    current = saveMode(storage, mode);
    applyMode(doc.documentElement, current);
    render();
  }

  for (var i = 0; i < toggles.length; i++) {
    toggles[i].addEventListener("click", function () {
      choose(toggledMode(current, query && query.matches));
    });
  }
  for (var j = 0; j < modeButtons.length; j++) {
    modeButtons[j].addEventListener("click", function () {
      choose(this.getAttribute("data-mode"));
    });
  }
  // 기기 설정을 따르는 중에 기기 모드가 바뀌면 PC 단추 이름도 바꿈
  if (query) {
    if (query.addEventListener) query.addEventListener("change", render);
    else if (query.addListener) query.addListener(render);
  }
  render();
}

if (typeof module !== "undefined") {
  module.exports = {
    THEME_KEY: THEME_KEY,
    normalizeMode: normalizeMode,
    readMode: readMode,
    saveMode: saveMode,
    themeAttr: themeAttr,
    isDarkMode: isDarkMode,
    toggledMode: toggledMode,
    toggleLabel: toggleLabel,
    applyMode: applyMode,
    initThemeControls: initThemeControls,
  };
} else if (typeof document !== "undefined") {
  // ① 첫 그리기 전에: head 에서 바로 실행 → 깜빡임 없음
  applyMode(document.documentElement, readMode(safeStorage(window)));
  // ② 단추는 본문이 생긴 뒤
  document.addEventListener("DOMContentLoaded", function () {
    initThemeControls(document, window);
  });
}
