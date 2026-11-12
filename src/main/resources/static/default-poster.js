// 사진 없는 행사 기본 그림 (확정 시안 P6 — docs/design/2026-11-13-improvement-drafts.md)
// 바탕 = 분류색(공연 · 전시 · 축제 · 그 밖), 내용 = 분류 라벨 · 행사 이름(세리프, 최대 3줄) · 장소
// 사진이 없거나 불러오기 실패한 모든 곳(지도 카드 · 목록 · 홈 · 상세)이 이 파일의 함수 하나로 그림 (모양은 css/default-poster.css)

// DB category 값(운영 11/13: 공연 · 행사 · 전시 · 축제 · 기타 · 스포츠) → 색 종류. 나머지는 모두 '그 밖'
var POSTER_KINDS = { 공연: "perform", 전시: "exhibit", 축제: "festival" };

function posterKind(category) {
  var key = String(category == null ? "" : category).trim();
  return Object.prototype.hasOwnProperty.call(POSTER_KINDS, key) ? POSTER_KINDS[key] : "etc";
}

// 위 라벨: 분류 이름 그대로, 비어 있으면 "행사"
function posterLabel(category) {
  var key = String(category == null ? "" : category).trim();
  return key || "행사";
}

// 아래 장소: place 칸, 없으면 원문(info)의 장소 줄 (event-detail.js 의 eventPlace 와 같은 규칙), 둘 다 없으면 ""
function posterPlace(event) {
  var place =
    typeof eventPlace === "function"
      ? eventPlace(event)
      : require("./event-detail.js").eventPlace(event); // node 시험
  return String(place || "").trim();
}

function posterEscape(text) {
  return String(text == null ? "" : text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// 기본 그림 HTML. className = 사진 자리의 크기 클래스(예: "event-thumb") → 사진과 같은 자리 · 크기에 들어감
// 제목 · 장소는 옆 카드 글자와 겹쳐 읽히지 않게 화면 낭독기에선 숨김(aria-hidden)
// 3줄 줄임표는 CSS(-webkit-line-clamp)가 함
function defaultPosterHtml(event, className) {
  event = event || {};
  var place = posterPlace(event);
  return (
    '<span class="default-poster dp-' + posterKind(event.category) +
    (className ? " " + posterEscape(className) : "") +
    '" aria-hidden="true"><span class="dp-inner">' +
    '<span class="dp-label">' + posterEscape(posterLabel(event.category)) + "</span>" +
    '<span class="dp-title">' + posterEscape(event.title || "") + "</span>" +
    (place ? '<span class="dp-place">' + posterEscape(place) + "</span>" : "") +
    "</span></span>"
  );
}

// 사진(img)을 못 불러오면 같은 자리 · 같은 크기 클래스의 기본 그림으로 바꿈
// CSP 가 onerror 속성을 막아서 addEventListener 로 연결. 바꾼 뒤 그 요소를 넘겨 줌 (onSwap)
function posterOnError(img, event, onSwap) {
  if (!img) return;
  img.addEventListener("error", function () {
    if (!img.getAttribute("src") || !img.parentNode) return; // src="" 로 비울 때 나는 error 는 무시
    var holder = document.createElement("span");
    holder.innerHTML = defaultPosterHtml(event, img.className);
    var poster = holder.firstChild;
    img.parentNode.replaceChild(poster, img);
    if (onSwap) onSwap(poster);
  });
}

if (typeof module !== "undefined") {
  module.exports = {
    posterKind: posterKind,
    posterLabel: posterLabel,
    posterPlace: posterPlace,
    defaultPosterHtml: defaultPosterHtml,
    posterOnError: posterOnError,
  };
}
