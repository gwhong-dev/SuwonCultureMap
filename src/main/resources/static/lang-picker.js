// 화면 언어 고르기 UI (11/13 확정 시안 LangE · LangD · LangMobile — docs/design/2026-11-13-todo-additions.md Q3)
//  - PC: 상단 바 ☾ 왼쪽 [지구 · 지금 언어 | Language ▾] → 펼침 목록 4줄 (위아래 화살표 · Enter · Esc · 바깥 누르기)
//  - 모바일: 홈 머리 [지구 KO] 알약 · ☰ 메뉴 "언어 Language" 줄 → 아래에서 올라오는 판
//  - 첫 방문 안내 줄: 아직 안 골랐고 브라우저 언어가 en · ja · zh 면 한 번 (PC = 상단 바 아래, 모바일 = 홈 맨 위)
// 언어 이름 · 저장은 i18n.js. 고르면 저장 뒤 새로고침 (JS 가 그린 글까지 그 언어로)
// 시험: node --test src/test/js/i18n.test.js (CI 에서 실행)

// 펼침 목록 · 판에서 위아래 화살표로 옮길 줄 번호 (끝에서 멈춤, 처음 · 끝 키 지원). 다른 키면 null
function langStep(index, key, count) {
  if (count <= 0) return null;
  if (key === "ArrowDown") return Math.min(index + 1, count - 1);
  if (key === "ArrowUp") return Math.max(index - 1, 0);
  if (key === "Home") return 0;
  if (key === "End") return count - 1;
  return null;
}

var LANG_GLOBE_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z" /></svg>';
var LANG_CHECK_SVG =
  '<svg class="lang-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>';

function initLangPicker(doc, win, i18n) {
  var current = i18n.lang();
  var names = i18n.NAMES;
  var langs = i18n.LANGS;
  var btnLabel = i18n.t("lang.buttonLabel", { name: names[current].native });

  function el(tag, className, text) {
    var node = doc.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function choose(lang) {
    if (lang === current) {
      i18n.set(lang, false); // 같은 언어여도 "골랐음" 으로 저장 (첫 방문 안내 줄이 다시 안 뜨게)
      return false;
    }
    i18n.set(lang, true);
    return true;
  }

  // 줄 하나: [그 언어 글자 이름] ... [영어 이름] [✓]
  function fillRow(row, lang) {
    var native = el("span", "lang-native", names[lang].native);
    native.setAttribute("lang", lang === "zh" ? "zh-Hans" : lang);
    var english = el("span", "lang-english", names[lang].english);
    english.setAttribute("lang", "en");
    row.appendChild(native);
    row.appendChild(english);
    row.insertAdjacentHTML("beforeend", LANG_CHECK_SVG);
  }

  // ===== PC: 상단 바 단추 + 펼침 목록 (LangE · LangD) =====
  var btn = doc.getElementById("langBtn");
  var pop = doc.getElementById("langPop");
  if (btn && pop) {
    btn.querySelector(".lang-current").textContent = names[current].native;
    btn.setAttribute("aria-label", btnLabel);
    var listbox = el("div", "lang-listbox");
    listbox.id = "langListbox";
    listbox.setAttribute("role", "listbox");
    listbox.setAttribute("aria-label", i18n.t("lang.listLabel"));
    var options = langs.map(function (lang) {
      var option = el("div", "lang-option");
      option.id = "langOption-" + lang;
      option.setAttribute("role", "option");
      option.setAttribute("tabindex", "-1");
      option.setAttribute("aria-selected", lang === current ? "true" : "false");
      option.dataset.lang = lang;
      fillRow(option, lang);
      option.addEventListener("click", function () {
        if (!choose(lang)) closePop(true);
      });
      listbox.appendChild(option);
      return option;
    });
    pop.appendChild(listbox);
    pop.appendChild(el("p", "lang-note", i18n.t("lang.note")));

    var openPop = function (focusIndex) {
      pop.hidden = false;
      btn.setAttribute("aria-expanded", "true");
      options[focusIndex].focus();
    };
    var closePop = function (returnFocus) {
      if (pop.hidden) return;
      pop.hidden = true;
      btn.setAttribute("aria-expanded", "false");
      if (returnFocus) btn.focus();
    };
    var currentIndex = langs.indexOf(current);
    btn.addEventListener("click", function (e) {
      e.stopPropagation();
      if (pop.hidden) openPop(currentIndex);
      else closePop(false);
    });
    btn.addEventListener("keydown", function (e) {
      if ((e.key === "ArrowDown" || e.key === "ArrowUp") && pop.hidden) {
        e.preventDefault();
        openPop(currentIndex);
      }
    });
    listbox.addEventListener("keydown", function (e) {
      var index = options.indexOf(doc.activeElement);
      var next = langStep(index < 0 ? currentIndex : index, e.key, options.length);
      if (next !== null) {
        e.preventDefault();
        options[next].focus();
      } else if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        if (index >= 0) options[index].click();
      } else if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation(); // 뒤에 열린 상세 · 메뉴까지 같이 닫히지 않게
        closePop(true);
      } else if (e.key === "Tab") {
        closePop(false);
      }
    });
    doc.addEventListener("click", function (e) {
      if (!pop.hidden && !pop.contains(e.target) && e.target !== btn) closePop(false);
    });
    // 창이 모바일 폭으로 줄면 PC 단추가 숨으니 닫음
    win.addEventListener("resize", function () {
      if (!pop.hidden && btn.offsetParent === null) closePop(false);
    });
  }

  // ===== 모바일: 아래에서 올라오는 판 (LangMobile) =====
  var sheet = null;
  var sheetReturn = null;
  function buildSheet() {
    var wrap = el("div", "lang-sheet-wrap");
    wrap.id = "langSheet";
    wrap.hidden = true;
    var panel = el("section", "lang-sheet");
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-modal", "true");
    panel.setAttribute("aria-labelledby", "langSheetTitle");
    panel.appendChild(el("span", "lang-sheet-handle"));
    var head = el("h2", "lang-sheet-title");
    head.id = "langSheetTitle";
    head.appendChild(el("span", "", i18n.t("lang.title")));
    if (current !== "en") {
      var en = el("span", "lang-sheet-en", "Language");
      en.setAttribute("lang", "en");
      head.appendChild(en);
    }
    panel.appendChild(head);
    var rows = el("div", "lang-rows");
    langs.forEach(function (lang) {
      var row = el("button", "lang-row");
      row.type = "button";
      row.dataset.lang = lang;
      row.setAttribute("aria-pressed", lang === current ? "true" : "false");
      fillRow(row, lang);
      row.addEventListener("click", function () {
        if (!choose(lang)) closeSheet();
      });
      rows.appendChild(row);
    });
    panel.appendChild(rows);
    panel.appendChild(el("p", "lang-note", i18n.t("lang.note")));
    wrap.appendChild(panel);
    wrap.addEventListener("click", function (e) {
      if (e.target === wrap) closeSheet(); // 뒤 덮개를 누르면 닫힘
    });
    wrap.addEventListener("keydown", function (e) {
      var list = Array.prototype.slice.call(rows.children);
      var index = list.indexOf(doc.activeElement);
      var next = index < 0 ? null : langStep(index, e.key, list.length);
      if (next !== null) {
        e.preventDefault();
        list[next].focus();
      } else if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        closeSheet();
      } else if (e.key === "Tab") {
        // 판 안에서만 돌게 (aria-modal)
        e.preventDefault();
        var step = e.shiftKey ? -1 : 1;
        list[(Math.max(index, 0) + step + list.length) % list.length].focus();
      }
    });
    doc.body.appendChild(wrap);
    return wrap;
  }
  function openSheet(from) {
    if (!sheet) sheet = buildSheet();
    sheetReturn = from || null;
    sheet.hidden = false;
    doc.body.classList.add("lang-sheet-open");
    var selected = sheet.querySelector('.lang-row[aria-pressed="true"]');
    if (selected) selected.focus();
  }
  function closeSheet() {
    if (!sheet || sheet.hidden) return;
    sheet.hidden = true;
    doc.body.classList.remove("lang-sheet-open");
    if (sheetReturn && sheetReturn.offsetParent !== null) sheetReturn.focus();
    sheetReturn = null;
  }

  var pill = doc.getElementById("homeLangBtn");
  if (pill) {
    pill.querySelector(".lang-code").textContent = names[current].code;
    pill.setAttribute("aria-label", btnLabel);
    pill.addEventListener("click", function () {
      openSheet(pill);
    });
  }
  var menuRow = doc.getElementById("menuLangBtn");
  if (menuRow) {
    // 영어 화면은 "Language Language" 로 겹치므로 뒤 영어 글을 숨김
    var menuEn = menuRow.querySelector(".menu-lang-en");
    if (menuEn && current === "en") menuEn.hidden = true;
    menuRow.setAttribute("aria-label", btnLabel);
    menuRow.addEventListener("click", function (e) {
      e.stopPropagation();
      // ☰ 메뉴는 닫고 판을 띄움 (index.js setMenuOpen) — 판을 닫으면 ☰ 단추로 초점
      if (typeof win.setMenuOpen === "function") win.setMenuOpen(false);
      openSheet(doc.getElementById("mobileMenuBtn"));
    });
  }

  // ===== 첫 방문 안내 줄 (LangE) =====
  var offer = i18n.offer();
  if (offer) {
    var banners = [];
    var dismiss = function () {
      i18n.set("ko", false); // 한국어로 저장 → 다시 안 뜸
      banners.forEach(function (banner) {
        banner.remove();
      });
      doc.body.classList.remove("lang-offer-on");
      // 지도 칸 크기가 바뀜 → 카카오맵 다시 맞춤 (index.js resize 처리)
      if (typeof win.dispatchEvent === "function" && typeof win.Event === "function") win.dispatchEvent(new win.Event("resize"));
    };
    var makeBanner = function (className) {
      var bar = el("div", "lang-offer " + className);
      bar.setAttribute("role", "region");
      bar.setAttribute("aria-label", i18n.tIn(offer, "lang.offerLabel"));
      bar.setAttribute("lang", offer === "zh" ? "zh-Hans" : offer);
      var body = el("div", "lang-offer-body");
      body.insertAdjacentHTML("beforeend", LANG_GLOBE_SVG);
      body.appendChild(el("span", "lang-offer-text", i18n.tIn(offer, "lang.offerText")));
      var actions = el("div", "lang-offer-actions");
      var yes = el("button", "lang-offer-yes", i18n.tIn(offer, "lang.offerButton"));
      yes.type = "button";
      yes.addEventListener("click", function () {
        choose(offer);
      });
      var keep = el("button", "lang-offer-keep", "한국어 그대로");
      keep.type = "button";
      keep.setAttribute("lang", "ko");
      keep.addEventListener("click", dismiss);
      actions.appendChild(yes);
      actions.appendChild(keep);
      body.appendChild(actions);
      bar.appendChild(body);
      var close = el("button", "lang-offer-close", "×");
      close.type = "button";
      close.setAttribute("aria-label", i18n.tIn(offer, "lang.offerClose"));
      close.addEventListener("click", dismiss);
      bar.appendChild(close);
      banners.push(bar);
      return bar;
    };
    var topbar = doc.getElementById("topbar");
    if (topbar) topbar.insertAdjacentElement("afterend", makeBanner("lang-offer-pc"));
    var home = doc.getElementById("homeView");
    if (home) home.insertBefore(makeBanner("lang-offer-mobile"), home.firstChild);
    doc.body.classList.add("lang-offer-on");
  }

  return { openSheet: openSheet, closeSheet: closeSheet };
}

if (typeof module !== "undefined") {
  module.exports = { langStep: langStep, initLangPicker: initLangPicker };
} else {
  initLangPicker(document, window, I18n);
}
