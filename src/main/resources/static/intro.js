// 첫 진입 애니메이션 — B안 확정 (11/13 사용자 결정, 3안 비교 · 버린 A · C 되살리기 = docs/decisions/2026-11-13-intro-animation-b.md)
// 흐름: 계절 꽃잎이 흩날림 → 0.7초부터 꽃잎이 모여 로고 → 1.5초 뒤 0.3초 동안 사라짐 (3~8월 벚꽃 · 9~2월 은행잎)
// 재생 조건: 이 기기에서 처음 들어왔을 때 1회 (localStorage "introSeen", 재생을 시작하면 건너뛰어도 본 것으로 기록)
//   저장소가 막혀 있으면(사생활 보호 모드 등) 기록을 못 해 매번 나올 수 있으므로 재생 안 함
//   미리 보기: ?intro=b 는 기록과 상관없이 매번 재생 (포트폴리오 시연용), ?season=spring|autumn 로 계절 고정
// 공통: 화면을 누르거나 아무 키나 누르면 바로 건너뜀, 움직임 줄이기(prefers-reduced-motion)면 재생 안 함
// body 맨 앞에서 동기로 읽어 지도보다 먼저 덮고, 지도는 그 아래에서 평소대로 불러옴 (모양 · 시간표는 css/intro.css)
// CSP 가 style 속성을 막아서 그림은 클래스 + SVG 속성으로만 그림 (style 속성 금지 — src/test/js/intro.test.js · StaticStylePolicyTest 가 확인)

var INTRO_SEEN_KEY = "introSeen";
var INTRO_TOTAL_MS = 1500; // 그림이 움직이는 시간 (css/intro.css 의 animation 시간과 같음)
var INTRO_FADE_MS = 300; // 사라지는 시간 (css/intro.css .intro 의 transition 과 같음)

// 주소의 검색 부분(location.search)에서 이름 하나 꺼내기. 없으면 ""
function introParam(search, name) {
  var m = new RegExp("[?&]" + name + "=([^&#]*)").exec(String(search || ""));
  if (!m) return "";
  try {
    return decodeURIComponent(m[1].replace(/\+/g, " ")).trim().toLowerCase();
  } catch (e) {
    return ""; // 깨진 %xx
  }
}

// 미리 보기: ?intro=b 일 때만 (옛 ?intro=a · ?intro=c 는 그냥 주소와 같음 — 처음 방문이면 1회 재생)
function isIntroPreview(search) {
  return introParam(search, "intro") === "b";
}

// 꽃잎 계절: ?season=spring|autumn 이 있으면 그 값, 없으면 달(1~12)로 3~8월 = spring(벚꽃), 9~2월 = autumn(은행잎)
function introSeason(month, search) {
  var forced = introParam(search, "season");
  if (forced === "spring" || forced === "autumn") return forced;
  return month >= 3 && month <= 8 ? "spring" : "autumn";
}

function safeStorage(win) {
  try {
    return win.localStorage || null;
  } catch (e) {
    return null;
  }
}

// 본 적 있나: true · false, 저장소가 없거나 막혀 있으면 null
function readIntroSeen(storage) {
  if (!storage) return null;
  try {
    return storage.getItem(INTRO_SEEN_KEY) !== null;
  } catch (e) {
    return null;
  }
}

// 본 것으로 기록. 성공하면 true
function markIntroSeen(storage) {
  if (!storage) return false;
  try {
    storage.setItem(INTRO_SEEN_KEY, "1");
    return true;
  } catch (e) {
    return false;
  }
}

// seen = readIntroSeen 결과. 처음(false)일 때만 재생, 본 적 있음(true) · 저장소 막힘(null)은 재생 안 함
function shouldPlayIntro(search, reducedMotion, seen) {
  if (reducedMotion) return false;
  if (isIntroPreview(search)) return true;
  return seen === false;
}

// 로고: W4 워드마크 / 부제 (static/brand/logo.svg 의 symbol, PC 상단 바 · 모바일 홈과 같은 그림 — 11/13 새 로고 B안, 도장 없음)
var INTRO_LOGO =
  '<div class="intro-logo">' +
  '<span class="intro-words"><svg class="intro-name"><use href="/brand/logo.svg#wordmark" /></svg>' +
  '<svg class="intro-sub"><use href="/brand/logo.svg#tagline" /></svg></span>' +
  "</div>";

// 꽃잎 하나 (원점 중심, 위치 · 움직임은 css 의 .ib-p1 ~ .ib-p12)
var INTRO_PETAL = {
  spring: "M0 -7 C4 -4 5 2 0 7 C-5 2 -4 -4 0 -7 Z M0 -7 L1 -9 L-1 -9 Z", // 벚꽃잎 (끝이 살짝 갈라짐)
  autumn: "M0 8 L-0.8 1 C-8 -1 -9 -7 -6 -9 C-3 -6 3 -6 6 -9 C9 -7 8 -1 0.8 1 Z", // 은행잎 (부채꼴)
};
var INTRO_PETAL_COUNT = 12;

// 덮개 HTML. 장식이라 화면 낭독기에선 숨김 (aria-hidden)
function introHtml(season) {
  var s = season === "autumn" ? "autumn" : "spring";
  var d = INTRO_PETAL[s];
  var out = '<div class="intro ib-' + s + '" aria-hidden="true"><svg class="intro-scene" viewBox="0 0 200 200">';
  for (var i = 1; i <= INTRO_PETAL_COUNT; i++) {
    out += '<g class="ib-petal ib-p' + i + '"><path d="' + d + '" /></g>';
  }
  return out + "</svg>" + INTRO_LOGO + "</div>";
}

// 재생: 조건이 맞으면 본 것으로 기록하고 body 에 덮개를 붙여 1.5초 뒤(또는 누르면 바로) 사라지게 함. 재생 안 하면 null
function startIntro(win, doc) {
  var reduced = !!(win.matchMedia && win.matchMedia("(prefers-reduced-motion: reduce)").matches);
  var search = win.location ? win.location.search : "";
  var storage = safeStorage(win);
  if (!shouldPlayIntro(search, reduced, readIntroSeen(storage))) return null;
  // 기록을 못 하면 다음에도 또 나오므로 재생 안 함 (미리 보기는 기록과 상관없이 재생)
  if (!markIntroSeen(storage) && !isIntroPreview(search)) return null;

  var holder = doc.createElement("div");
  holder.innerHTML = introHtml(introSeason(new Date().getMonth() + 1, search));
  var el = holder.firstChild;
  doc.body.appendChild(el);

  var done = false;
  var timer = null;
  function finish(e) {
    // 건너뛰려고 누른 키가 아래 화면(카드 넘기기 ← → · 상세 닫기 Esc 등)까지 가지 않게 여기서 멈춤 (document 캡처 단계)
    if (e && e.type === "keydown") {
      e.preventDefault();
      e.stopPropagation();
    }
    if (done) return;
    done = true;
    win.clearTimeout(timer);
    doc.removeEventListener("keydown", finish, true);
    el.classList.add("intro-out"); // 사라지는 동안에도 덮개가 누름을 받아 아래 지도가 눌리지 않음
    win.setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, INTRO_FADE_MS);
  }
  el.addEventListener("click", finish);
  doc.addEventListener("keydown", finish, true);
  timer = win.setTimeout(finish, INTRO_TOTAL_MS);
  return { el: el, finish: finish };
}

if (typeof module !== "undefined") {
  module.exports = {
    INTRO_SEEN_KEY: INTRO_SEEN_KEY,
    INTRO_TOTAL_MS: INTRO_TOTAL_MS,
    INTRO_FADE_MS: INTRO_FADE_MS,
    INTRO_PETAL_COUNT: INTRO_PETAL_COUNT,
    isIntroPreview: isIntroPreview,
    introSeason: introSeason,
    readIntroSeen: readIntroSeen,
    markIntroSeen: markIntroSeen,
    shouldPlayIntro: shouldPlayIntro,
    introHtml: introHtml,
    startIntro: startIntro,
  };
} else if (typeof document !== "undefined" && document.body) {
  startIntro(window, document);
}
