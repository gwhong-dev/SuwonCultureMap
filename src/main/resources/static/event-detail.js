// 상세 화면 표시 규칙 — 인스타 행사처럼 info 원문 · 사진이 없을 때 장소 · 시간 · 출처로 채움
// 재단 행사(info 있음)는 지금처럼 info 원문 기준. 시험: node --test src/test/js/ (CI 에서 실행)

// ===== info 텍스트에서 "장소" 값만 뽑기 (place 칸이 빌 때만 씀) =====
// 서버(InfoParser.findPlace)와 같은 규칙: 라벨이 정확히 "장소" → "주소/소재지" → "주소" 순 (빈 값은 건너뜀)
// 운영 재단 121건 점검(11/9): 장소 121 · 주소/소재지 3 · 주소 1 — "행사장소" 같은 라벨은 없음
var PLACE_INFO_LABELS = ["장소", "주소/소재지", "주소"];

function extractPlaceName(infoText) {
  if (!infoText) return null;
  var lines = infoText.split("\n");
  for (var i = 0; i < PLACE_INFO_LABELS.length; i++) {
    for (var j = 0; j < lines.length; j++) {
      var idx = lines[j].indexOf(":");
      if (idx === -1) continue;
      if (lines[j].substring(0, idx).trim() !== PLACE_INFO_LABELS[i]) continue;
      var value = lines[j].substring(idx + 1).trim();
      if (value) return value;
    }
  }
  return null;
}

// 장소명: 서버가 꺼낸 place 칸(재단 = XML 장소 쌍, 인스타 = 캡션) 먼저, 비면 info 원문에서 같은 규칙으로
function eventPlace(event) {
  return event.place || extractPlaceName(event.info);
}

// 수원문화DB 행사: info 칸에는 원문이 아니라 "장소가 여러 곳 · 원문 확인" 안내만 들어 있음 (소개글은 가져오지 않음)
function isSwdbEvent(event) {
  return event.source === "SWDB";
}

// info 칸이 재단 원문인지 (인스타 = 없음, 수원문화DB = 안내 글)
function hasOriginalInfo(event) {
  return !!event.info && !isSwdbEvent(event);
}

// 시간: info 원문이 없을 때만 timeText 칸 (있으면 원문에서 찾은 값을 씀 → null)
function fallbackTime(event) {
  if (hasOriginalInfo(event)) return null;
  return event.timeText || null;
}

// 상세 정보 분해 (V7): 서버가 원문에서 꺼낸 요금 · 문의처 · 주최 칸. 동기화 전(옛 행)이면 null → 화면이 원문에서 찾음
function detailFields(event) {
  return {
    fee: event.fee || null,
    contact: event.contact || null,
    host: event.host || null,
  };
}

function isInstagramEvent(event) {
  return event.source === "INSTAGRAM";
}

// 화면 언어 (i18n.js, node 시험 = 한국어)
var detailI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;

// 출처 한 줄: 인스타 행사 ("인스타그램 @계정") · 수원문화DB 행사. 재단 행사는 null → 표시 안 함
function sourceLabel(event) {
  if (isSwdbEvent(event)) return detailI18n.t("detail.swdb");
  if (!isInstagramEvent(event)) return null;
  return event.sourceAccount ? detailI18n.t("detail.instagram") + " @" + event.sourceAccount : detailI18n.t("detail.instagram");
}

// 원문 링크는 http · https 주소만 href 로 씀 (다른 형식은 버튼을 숨김)
function safeLink(url) {
  if (typeof url !== "string") return null;
  var trimmed = url.trim();
  return /^https?:\/\//i.test(trimmed) ? trimmed : null;
}

function linkButtonText(event) {
  return detailI18n.t(isInstagramEvent(event) ? "detail.viewInstagram" : "detail.viewSource");
}

// PC 상세 본문: info 원문, 없으면 장소 · 시간 · 출처 줄 (셋 다 없으면 ""). 수원문화DB 안내 글은 그 줄들 위에
function detailInfoText(event) {
  if (hasOriginalInfo(event)) return event.info;
  var lines = [];
  if (event.info) lines.push(event.info, "");
  if (event.place) lines.push(detailI18n.t("detail.place") + ": " + event.place);
  if (event.timeText) lines.push(detailI18n.t("detail.time") + ": " + event.timeText);
  var source = sourceLabel(event);
  if (source) lines.push(detailI18n.t("detail.source") + ": " + source);
  return lines.join("\n");
}

// 목록 카드 사진: http · https 주소만 (없으면 null → 사진 칸 없이 글만 그림)
function cardImageUrl(event) {
  return safeLink(event.imageUrl);
}

// 목록 카드 요약 (info 원문이 없을 때): 장소 · 시간 줄. info 가 있으면 "" (기존 이용료/장소/시간 요약을 씀)
function cardFallbackInfo(event) {
  if (hasOriginalInfo(event)) return "";
  var lines = [];
  if (event.place) lines.push(detailI18n.t("detail.place") + ": " + event.place);
  if (event.timeText) lines.push(detailI18n.t("detail.time") + ": " + event.timeText);
  return lines.join("\n");
}

// 무료 행사 판정 ("💸 무료" 칩) — 요금 칸(V7), 없으면(동기화 전) 원문의 요금 계열 줄
// 운영 재단 121건 점검(11/9): "무료" 54 · "무 료" · "전석 무료" · "무료 입장" 은 무료,
// "무료(일부 공연 및 프로그램 유료)" 처럼 유료가 섞이거나 "어린이 무료, 성인 10,000원" 처럼 금액(숫자)이 있으면 무료 아님. 요금 정보가 없는 행사(인스타 등)도 무료 아님
var FEE_INFO_LABELS = ["이용료", "관람료", "참가비용", "참가비", "입장료", "요금"];

function eventFeeText(event) {
  if (event.fee) return event.fee;
  var lines = (event.info || "").split("\n");
  for (var i = 0; i < FEE_INFO_LABELS.length; i++) {
    for (var j = 0; j < lines.length; j++) {
      var idx = lines[j].indexOf(":");
      if (idx === -1) continue;
      if (lines[j].substring(0, idx).trim() !== FEE_INFO_LABELS[i]) continue; // 정확히 같은 라벨만 ("주차요금" 제외)
      var value = lines[j].substring(idx + 1).trim();
      if (value) return value;
    }
  }
  return null;
}

function isFreeEvent(event) {
  var fee = eventFeeText(event);
  if (!fee) return false;
  var compact = fee.replace(/\s+/g, "");
  return compact.indexOf("무료") !== -1 && compact.indexOf("유료") === -1 && !/\d/.test(compact);
}

if (typeof module !== "undefined") {
  module.exports = {
    extractPlaceName: extractPlaceName,
    eventPlace: eventPlace,
    fallbackTime: fallbackTime,
    detailFields: detailFields,
    sourceLabel: sourceLabel,
    safeLink: safeLink,
    linkButtonText: linkButtonText,
    detailInfoText: detailInfoText,
    cardImageUrl: cardImageUrl,
    cardFallbackInfo: cardFallbackInfo,
    hasOriginalInfo: hasOriginalInfo,
    eventFeeText: eventFeeText,
    isFreeEvent: isFreeEvent,
  };
}
