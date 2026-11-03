// 찜 안내 문구 — 토스트 · 찜 탭 빈 상태 (docs/design/mobile-design-v1.md 2번 토스트 · 05 찜)
// 찜은 로그인 회원만 (서버 FavoriteService). 시험: node --test src/test/js/ (CI 에서 실행)

// 찜 탭이 비었을 때 보여 줄 안내
// favoriteCount = 지금 받아 온 행사 중 찜한 개수 (0 보다 크면 검색어 · 날짜 · 지난 행사 조건에 걸러진 것)
// action: "login" = 로그인 화면, "browse" = 전체 행사로, null = 버튼 없음
function favoriteEmptyState(isLoggedIn, favoriteCount) {
  if (!isLoggedIn) {
    return {
      title: "로그인하고 찜해 보세요",
      body: "관심 있는 행사의 ♡를 누르면 여기에 모여요",
      actionLabel: "로그인",
      action: "login",
    };
  }
  if (favoriteCount === 0) {
    return {
      title: "아직 찜한 행사가 없어요",
      body: "마음에 드는 행사의 ♡를 눌러 보세요",
      actionLabel: "행사 둘러보기",
      action: "browse",
    };
  }
  return {
    title: "조건에 맞는 찜한 행사가 없어요",
    body: "검색어 · 날짜 · 지난 행사 설정을 확인해 보세요",
    actionLabel: null,
    action: null,
  };
}

// 찜 결과 토스트: 담으면 "보기"(→ 찜 탭) 버튼, 해제는 글만
function favoriteToast(added) {
  return added
    ? { text: "찜 목록에 담았어요", actionLabel: "보기" }
    : { text: "찜을 해제했어요", actionLabel: null };
}

var FAVORITE_LOGIN_TOAST = { text: "로그인하면 찜할 수 있어요", actionLabel: "로그인" };

// 실패 토스트: 서버가 보낸 안내 글(예: "로그인이 필요합니다.")이 있으면 그대로, 없으면(네트워크 끊김 등) 기본 문구
function favoriteErrorText(serverText) {
  var text = (serverText || "").trim();
  // 짧은 글만 안내로 씀 (HTML 오류 화면 같은 긴 본문은 사용자에게 보이지 않게)
  return text && text.length <= 60 && text.indexOf("<") === -1
    ? text
    : "찜을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요";
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
