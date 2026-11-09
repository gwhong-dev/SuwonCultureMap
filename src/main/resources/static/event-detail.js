// 상세 화면 표시 규칙 — 인스타 행사처럼 info 원문 · 사진이 없을 때 장소 · 시간 · 출처로 채움
// 재단 행사(info 있음)는 지금처럼 info 원문 기준. 시험: node --test src/test/js/ (CI 에서 실행)

// ===== info 텍스트에서 "장소" 값만 뽑기 (목록 팝업 제목용) =====
// 라벨이 "장소" 하나로만 안 오고 "행사장소", "공연장소" 등으로도 와서
// 포함 여부로 찾음. "장소" 계열이 아예 없으면 "주소/소재지"로 대체
function extractPlaceName(infoText) {
  if (!infoText) return null;
  var lines = infoText.split("\n");
  var addressFallback = null;

  for (var i = 0; i < lines.length; i++) {
    var idx = lines[i].indexOf(":");
    if (idx === -1) continue;
    var label = lines[i].substring(0, idx).trim();
    var value = lines[i].substring(idx + 1).trim();

    if (label.indexOf("장소") !== -1) {
      return value;
    }
    if (!addressFallback && label.indexOf("주소") !== -1) {
      addressFallback = value;
    }
  }

  return addressFallback;
}

// 장소명: info 원문이 있으면 거기서만 (재단 행사는 그대로), 없으면 place 칸 (인스타 캡션에서 뽑은 장소)
function eventPlace(event) {
  if (event.info) return extractPlaceName(event.info);
  return event.place || null;
}

// 시간: info 원문이 없을 때만 timeText 칸 (있으면 원문에서 찾은 값을 씀 → null)
function fallbackTime(event) {
  if (event.info) return null;
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

// 출처 한 줄: 인스타 행사만 ("인스타그램 @계정"). 재단 행사는 null → 표시 안 함
function sourceLabel(event) {
  if (!isInstagramEvent(event)) return null;
  return event.sourceAccount ? "인스타그램 @" + event.sourceAccount : "인스타그램";
}

// 원문 링크는 http · https 주소만 href 로 씀 (다른 형식은 버튼을 숨김)
function safeLink(url) {
  if (typeof url !== "string") return null;
  var trimmed = url.trim();
  return /^https?:\/\//i.test(trimmed) ? trimmed : null;
}

function linkButtonText(event) {
  return isInstagramEvent(event) ? "인스타그램에서 보기 →" : "원문 보기 →";
}

// PC 상세 본문: info 원문, 없으면 장소 · 시간 · 출처 줄 (셋 다 없으면 "")
function detailInfoText(event) {
  if (event.info) return event.info;
  var lines = [];
  if (event.place) lines.push("장소: " + event.place);
  if (event.timeText) lines.push("시간: " + event.timeText);
  var source = sourceLabel(event);
  if (source) lines.push("출처: " + source);
  return lines.join("\n");
}

// 목록 카드 사진: http · https 주소만 (없으면 null → 사진 칸 없이 글만 그림)
function cardImageUrl(event) {
  return safeLink(event.imageUrl);
}

// 목록 카드 요약 (info 원문이 없을 때): 장소 · 시간 줄. info 가 있으면 "" (기존 이용료/장소/시간 요약을 씀)
function cardFallbackInfo(event) {
  if (event.info) return "";
  var lines = [];
  if (event.place) lines.push("장소: " + event.place);
  if (event.timeText) lines.push("시간: " + event.timeText);
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
    eventFeeText: eventFeeText,
    isFreeEvent: isFreeEvent,
  };
}
