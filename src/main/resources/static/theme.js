// 화면 모드 (기기 설정 · 라이트 · 다크) — 모든 페이지 head 에서 css 보다 먼저, 동기로 읽음 (인라인 스크립트는 CSP 로 막힘)
// 저장: localStorage "theme" = "system" | "light" | "dark" (없거나 틀린 값 = system)
// 적용: <html data-theme="light|dark"> — system 이면 속성을 지워 css/theme.css 의 prefers-color-scheme 를 따름
// 단추: PC 상단 바 ☾/☀ (두 단계, [data-theme-toggle]) · 모바일 ☰ 메뉴 "화면 모드" 3칸 ([data-theme-mode] 안의 [data-mode])
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

// PC 단추 모양: 밝을 때 ☾ "어두운 화면으로", 어두울 때 ☀ "밝은 화면으로"
function toggleLabel(mode, systemDark) {
  return isDarkMode(mode, systemDark)
    ? { icon: "sun", label: "밝은 화면으로" }
    : { icon: "moon", label: "어두운 화면으로" };
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

var THEME_ICONS = {
  moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" /></svg>',
  sun: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" /></svg>',
};

// 단추 연결 (본문이 그려진 뒤). 단추가 없는 페이지(about 등)는 아무것도 안 함
function initThemeControls(doc, win) {
  var storage = safeStorage(win);
  var query = systemDarkQuery(win);
  var toggles = doc.querySelectorAll("[data-theme-toggle]");
  var modeButtons = doc.querySelectorAll("[data-theme-mode] [data-mode]");

  function render() {
    var mode = readMode(storage);
    var look = toggleLabel(mode, query && query.matches);
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].innerHTML = THEME_ICONS[look.icon];
      toggles[i].setAttribute("aria-label", look.label);
      toggles[i].setAttribute("title", look.label);
    }
    for (var j = 0; j < modeButtons.length; j++) {
      modeButtons[j].setAttribute("aria-pressed", String(modeButtons[j].getAttribute("data-mode") === mode));
    }
  }
  function choose(mode) {
    applyMode(doc.documentElement, saveMode(storage, mode));
    render();
  }

  for (var i = 0; i < toggles.length; i++) {
    toggles[i].addEventListener("click", function () {
      choose(toggledMode(readMode(storage), query && query.matches));
    });
  }
  for (var j = 0; j < modeButtons.length; j++) {
    modeButtons[j].addEventListener("click", function (e) {
      e.stopPropagation(); // ☰ 메뉴가 닫히지 않게
      choose(this.getAttribute("data-mode"));
    });
  }
  // 기기 설정을 따르는 중에 기기 모드가 바뀌면 PC 단추 모양도 바꿈
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
