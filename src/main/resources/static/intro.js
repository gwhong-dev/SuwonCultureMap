// 첫 진입 애니메이션 3안 (확정 시안 P7 — docs/design/2026-11-13-improvement-drafts.md "P7")
// 지금은 사용자가 셋을 보고 고르는 단계: 주소 ?intro=a · ?intro=b · ?intro=c 일 때만 매번 재생, 그냥 들어오면 재생 안 함
//   a = 닫힌 성문 → 0.7초 문 열림 + 도장 → 1.5초 문 안으로 확대 → 지도
//   b = 계절 꽃잎이 흩날림 → 0.7초부터 꽃잎이 모여 로고 (봄 · 여름 벚꽃, 가을 · 겨울 은행잎. ?season=spring|autumn 로 미리 보기)
//   c = 장안문 → 0.5초 방화수류정 → 1초 로고 → 지도
// 공통: 1.5초 뒤 0.3초 동안 사라짐, 화면을 누르거나 아무 키나 누르면 바로 건너뜀, 움직임 줄이기(prefers-reduced-motion)면 재생 안 함
// body 맨 앞에서 동기로 읽어 지도보다 먼저 덮고, 지도는 그 아래에서 평소대로 불러옴 (모양 · 시간표는 css/intro.css)
// CSP 가 style 속성을 막아서 그림은 클래스 + SVG 속성으로만 그림 (style 속성 금지 — src/test/js/intro.test.js · StaticStylePolicyTest 가 확인)

var INTRO_VARIANTS = ["a", "b", "c"];
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

// ?intro= 값 → "a" | "b" | "c" | null (모르는 값 · 빈 값은 재생 안 함)
function introVariant(search) {
  var v = introParam(search, "intro");
  return INTRO_VARIANTS.indexOf(v) >= 0 ? v : null;
}

// 2안 꽃잎 계절: ?season=spring|autumn 이 있으면 그 값, 없으면 달(1~12)로 3~8월 = spring(벚꽃), 9~2월 = autumn(은행잎)
function introSeason(month, search) {
  var forced = introParam(search, "season");
  if (forced === "spring" || forced === "autumn") return forced;
  return month >= 3 && month <= 8 ? "spring" : "autumn";
}

function shouldPlayIntro(search, reducedMotion) {
  return !reducedMotion && introVariant(search) !== null;
}

// 로고: 도장 + 워드마크 / 부제 (static/brand/logo.svg 의 symbol, PC 상단 바 · 모바일 홈과 같은 그림)
var INTRO_LOGO =
  '<div class="intro-logo">' +
  '<svg class="intro-seal"><use href="/brand/logo.svg#seal" /></svg>' +
  '<span class="intro-words"><svg class="intro-name"><use href="/brand/logo.svg#wordmark" /></svg>' +
  '<svg class="intro-sub"><use href="/brand/logo.svg#tagline" /></svg></span>' +
  "</div>";

// 기와지붕 한 층: 처마 가운데가 처지고(y) 양 끝이 들림(y - 0.35h), 위는 용마루(y - h)
function introRoof(x1, x2, y, h) {
  var mid = (x1 + x2) / 2;
  var w = x2 - x1;
  var tip = y - h * 0.35;
  return (
    "M" + x1 + " " + tip +
    " Q" + (x1 + w * 0.3) + " " + y + " " + mid + " " + y +
    " Q" + (x2 - w * 0.3) + " " + y + " " + x2 + " " + tip +
    " Q" + (x2 - w * 0.1) + " " + (y - h * 0.7) + " " + (x2 - w * 0.18) + " " + (y - h) +
    " H" + (x1 + w * 0.18) +
    " Q" + (x1 + w * 0.1) + " " + (y - h * 0.7) + " " + x1 + " " + tip + " Z"
  );
}

// 1안: 성벽 + 문루(2층 지붕) + 아치 문 두 짝, 문루 가운데에 도장
function introGateA() {
  var arch = "M76 150 V114 A24 24 0 0 1 124 114 V150 Z";
  return (
    '<svg class="intro-scene" viewBox="0 0 200 160">' +
    '<defs><clipPath id="introArch"><path d="' + arch + '" /></clipPath></defs>' +
    '<g class="ia-zoom">' +
    '<path class="ix-stone" d="M14 150 V84 H186 V150 Z M14 84 V76 H28 V84 Z M42 84 V76 H56 V84 Z M144 84 V76 H158 V84 Z M172 84 V76 H186 V84 Z" />' +
    '<path class="ix-wood" d="M58 84 V66 H142 V84 Z" />' +
    '<path class="ix-roof" d="' + introRoof(44, 156, 68, 14) + '" />' +
    '<path class="ix-wood" d="M74 54 V44 H126 V54 Z" />' +
    '<path class="ix-roof" d="' + introRoof(62, 138, 46, 14) + '" />' +
    '<path class="ix-hole" d="' + arch + '" />' +
    '<g clip-path="url(#introArch)">' +
    '<g class="ia-door ia-door-l"><rect class="ix-door" x="76" y="88" width="24" height="62" /><path class="ix-stud" d="M80 100 h16 M80 116 h16 M80 132 h16" /></g>' +
    '<g class="ia-door ia-door-r"><rect class="ix-door" x="100" y="88" width="24" height="62" /><path class="ix-stud" d="M104 100 h16 M104 116 h16 M104 132 h16" /></g>' +
    "</g>" +
    '<use class="ia-seal" href="/brand/logo.svg#seal" x="91" y="66" width="18" height="18" />' +
    "</g></svg>"
  );
}

// 2안 꽃잎 하나 (원점 중심, 위치 · 움직임은 css 의 .ib-p1 ~ .ib-p12)
var INTRO_PETAL = {
  spring: "M0 -7 C4 -4 5 2 0 7 C-5 2 -4 -4 0 -7 Z M0 -7 L1 -9 L-1 -9 Z", // 벚꽃잎 (끝이 살짝 갈라짐)
  autumn: "M0 8 L-0.8 1 C-8 -1 -9 -7 -6 -9 C-3 -6 3 -6 6 -9 C9 -7 8 -1 0.8 1 Z", // 은행잎 (부채꼴)
};
var INTRO_PETAL_COUNT = 12;

function introPetalsB(season) {
  var d = INTRO_PETAL[season] || INTRO_PETAL.spring;
  var out = '<svg class="intro-scene" viewBox="0 0 200 200">';
  for (var i = 1; i <= INTRO_PETAL_COUNT; i++) {
    out += '<g class="ib-petal ib-p' + i + '"><path d="' + d + '" /></g>';
  }
  return out + "</svg>" + INTRO_LOGO;
}

// 3안: 장안문(문루 2층 + 홍예문) → 방화수류정(물가 정자) → 로고
function introLandmarksC() {
  return (
    '<svg class="intro-scene" viewBox="0 0 200 160">' +
    '<g class="ic-gate">' +
    '<path class="ix-stone" fill-rule="evenodd" d="M24 150 V96 H176 V150 Z M84 150 V124 A16 16 0 0 1 116 124 V150 Z" />' +
    '<path class="ix-wood" d="M50 96 V78 H150 V96 Z" />' +
    '<path class="ix-roof" d="' + introRoof(36, 164, 80, 16) + '" />' +
    '<path class="ix-wood" d="M70 64 V54 H130 V64 Z" />' +
    '<path class="ix-roof" d="' + introRoof(56, 144, 56, 16) + '" />' +
    "</g>" +
    '<g class="ic-pavilion">' +
    '<path class="ix-water" d="M14 152 q10 -5 20 0 t20 0 t20 0 t20 0 t20 0 t20 0 t20 0 t20 0 t20 0" />' +
    '<path class="ix-stone" d="M42 142 L58 118 H142 L158 142 Z" />' +
    '<path class="ix-wood" d="M64 118 V90 H70 V118 Z M97 118 V90 H103 V118 Z M130 118 V90 H136 V118 Z M64 104 H136 V108 H64 Z" />' +
    '<path class="ix-roof" d="' + introRoof(42, 158, 92, 16) + '" />' +
    '<path class="ix-roof" d="M78 78 L100 56 L122 78 Z" />' +
    "</g>" +
    "</svg>" +
    INTRO_LOGO
  );
}

// 덮개 HTML. 장식이라 화면 낭독기에선 숨김 (aria-hidden)
function introHtml(variant, season) {
  var body =
    variant === "a" ? introGateA() :
    variant === "b" ? introPetalsB(season) :
    variant === "c" ? introLandmarksC() : "";
  if (!body) return "";
  var cls = "intro intro-" + variant + (variant === "b" ? " ib-" + (season === "autumn" ? "autumn" : "spring") : "");
  return '<div class="' + cls + '" aria-hidden="true">' + body + "</div>";
}

// 재생: 조건이 맞으면 body 에 덮개를 붙이고 1.5초 뒤(또는 누르면 바로) 사라지게 함. 재생 안 하면 null
function startIntro(win, doc) {
  var reduced = !!(win.matchMedia && win.matchMedia("(prefers-reduced-motion: reduce)").matches);
  var search = win.location ? win.location.search : "";
  if (!shouldPlayIntro(search, reduced)) return null;

  var variant = introVariant(search);
  var holder = doc.createElement("div");
  holder.innerHTML = introHtml(variant, introSeason(new Date().getMonth() + 1, search));
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
    INTRO_TOTAL_MS: INTRO_TOTAL_MS,
    INTRO_FADE_MS: INTRO_FADE_MS,
    INTRO_PETAL_COUNT: INTRO_PETAL_COUNT,
    introVariant: introVariant,
    introSeason: introSeason,
    shouldPlayIntro: shouldPlayIntro,
    introHtml: introHtml,
    startIntro: startIntro,
  };
} else if (typeof document !== "undefined" && document.body) {
  startIntro(window, document);
}
