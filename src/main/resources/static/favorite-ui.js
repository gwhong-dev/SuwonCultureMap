// 찜 안내 문구 — 토스트 · 찜 탭 빈 상태 (docs/design/mobile-design-v1.md 2번 토스트 · 05 찜)
// 회원 찜 = 서버(FavoriteService), 비회원 찜 = 이 기기(guest-favorites.js). 시험: node --test src/test/js/ (CI 에서 실행)

// 화면 언어 (i18n.js, node 시험 = 한국어)
var favI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;

// 찜 탭이 비었을 때 보여 줄 안내
// favoriteCount = 지금 받아 온 행사 중 찜한 개수 (0 보다 크면 검색어 · 날짜 · 지난 행사 조건에 걸러진 것)
// action: "login" = 로그인 화면, "browse" = 전체 행사로, null = 버튼 없음
function favoriteEmptyState(isLoggedIn, favoriteCount) {
  if (favoriteCount === 0 && !isLoggedIn) {
    // 비회원도 찜할 수 있음 (이 기기에 저장, 로그인하면 계정에 합침)
    return {
      title: favI18n.t("fav.emptyTitle"),
      body: favI18n.t("fav.emptyGuest"),
      actionLabel: favI18n.t("fav.browse"),
      action: "browse",
    };
  }
  if (favoriteCount === 0) {
    return {
      title: favI18n.t("fav.emptyTitle"),
      body: favI18n.t("fav.emptyMember"),
      actionLabel: favI18n.t("fav.browse"),
      action: "browse",
    };
  }
  return {
    title: favI18n.t("fav.noMatchTitle"),
    body: favI18n.t("fav.noMatchBody"),
    actionLabel: null,
    action: null,
  };
}

// 찜 결과 토스트: 담으면 "보기"(→ 찜 탭) 버튼, 해제는 글만
function favoriteToast(added) {
  return added
    ? { text: favI18n.t("fav.added"), actionLabel: favI18n.t("fav.view") }
    : { text: favI18n.t("fav.removed"), actionLabel: null };
}

// 로그인이 풀린 채(세션 만료) ♡ 를 누른 경우
var FAVORITE_LOGIN_TOAST = { text: favI18n.t("fav.loginExpired"), actionLabel: favI18n.t("nav.login") };

// 실패 토스트: 서버가 보낸 안내 글(예: "로그인이 필요합니다.")이 있으면 그대로, 없으면(네트워크 끊김 등) 기본 문구
function favoriteErrorText(serverText) {
  var text = (serverText || "").trim();
  // 짧은 글만 안내로 씀 (HTML 오류 화면 같은 긴 본문은 사용자에게 보이지 않게)
  // 서버 안내 글은 한국어라 다른 언어 화면에서는 기본 문구 (서버 글 번역은 나중)
  return text && text.length <= 60 && text.indexOf("<") === -1 && favI18n.lang() === "ko"
    ? text
    : favI18n.t("fav.saveFailed");
}

// 세션이 만료된 채로 ♡ 를 누른 경우: 서버(FavoriteController)가 400 "로그인이 필요합니다." 를 보냄
function isLoginRequiredError(serverText) {
  return (serverText || "").trim() === "로그인이 필요합니다.";
}

if (typeof module !== "undefined") {
  module.exports = {
    favoriteEmptyState: favoriteEmptyState,
    favoriteToast: favoriteToast,
    FAVORITE_LOGIN_TOAST: FAVORITE_LOGIN_TOAST,
    favoriteErrorText: favoriteErrorText,
    isLoginRequiredError: isLoginRequiredError,
  };
}
