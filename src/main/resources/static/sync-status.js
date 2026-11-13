// 데이터 갱신 시각 줄 "행사 정보 마지막 갱신 · 11/11 06:00" — 재단 동기화(06 · 18시)가 마지막으로 성공한 시각
// GET /api/sync-status 의 lastSuccessAt 은 서버가 한국 시간 값으로 줌("2026-11-11T06:00:12") → 숫자만 꺼내 씀(기기 시간대와 무관)
// 실패 정보는 보이지 않음. API 실패 · 값 없음 · 형식이 틀리면 줄 자체를 숨김
// 자리: 마이 페이지(mypage.html) 맨 아래 정보 칸 — data-sync-status 가 붙은 칸 전부 (11/11 사용자 결정: 목록 줄에는 안 넣음)
// 시험: node --test src/test/js/ (CI 에서 실행)

// 화면 언어 (i18n.js, node 시험 = 한국어)
var syncI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;

// "2026-11-11T06:00:12" → "행사 정보 마지막 갱신 · 11/11 06:00" (M/D HH:mm). 틀린 값이면 null
function syncStatusText(lastSuccessAt) {
  if (typeof lastSuccessAt !== "string") return null;
  var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(lastSuccessAt);
  if (!m) return null;
  var month = Number(m[2]);
  var day = Number(m[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31 || Number(m[4]) > 23 || Number(m[5]) > 59) return null;
  return syncI18n.t("sync.updated") + " · " + month + "/" + day + " " + m[4] + ":" + m[5];
}

// 칸마다 글을 넣고 보이거나(text 있음) 숨김(null)
function showSyncStatus(elements, text) {
  Array.prototype.forEach.call(elements, function (el) {
    el.textContent = text || "";
    el.hidden = !text;
  });
}

// fetchFn: 브라우저 fetch (시험에서는 가짜). 끝나면 보인 글(없으면 null)로 끝나는 Promise
function loadSyncStatus(fetchFn, elements) {
  return fetchFn("/api/sync-status", { headers: { Accept: "application/json" } })
    .then(function (response) {
      if (!response.ok) throw new Error("HTTP " + response.status);
      return response.json();
    })
    .then(function (data) {
      return data ? syncStatusText(data.lastSuccessAt) : null;
    })
    .catch(function () {
      return null;
    })
    .then(function (text) {
      showSyncStatus(elements, text);
      return text;
    });
}

if (typeof module !== "undefined") {
  module.exports = {
    syncStatusText: syncStatusText,
    showSyncStatus: showSyncStatus,
    loadSyncStatus: loadSyncStatus,
  };
} else if (typeof document !== "undefined" && typeof fetch === "function") {
  // 화면: 스크립트가 body 끝에 있어 칸이 이미 있음
  loadSyncStatus(fetch.bind(window), document.querySelectorAll("[data-sync-status]"));
}
