// 최근 본 행사 — 상세를 연 행사 번호를 이 기기 브라우저(localStorage)에만 최근 순으로 저장 (서버로 보내지 않음)
// 찜(서버 FavoriteService, 로그인 회원만)과 따로, 로그인 없이 씀. 찜 탭 "찜 N | 최근 본 N" (docs/design/mobile-design-v1.md 05)
// 시험: node --test src/test/js/ (CI 에서 실행)

var RECENT_VIEWS_KEY = "suwonbible.recentViews";
var RECENT_VIEWS_MAX = 30;

// 방금 본 행사를 맨 앞에 (이미 있으면 앞으로 옮김), 최대 개수까지만. 원래 배열은 그대로 두고 새 배열
function addRecentView(ids, id) {
  return [id]
    .concat(
      ids.filter(function (x) {
        return x !== id;
      }),
    )
    .slice(0, RECENT_VIEWS_MAX);
}

// 저장된 글 → 행사 번호 목록. 깨졌거나 손댄 값은 버림 (양의 정수만, 중복 없이, 최대 개수까지)
function parseRecentViews(raw) {
  var list;
  try {
    list = JSON.parse(raw);
  } catch (e) {
    return [];
  }
  if (!Array.isArray(list)) return [];
  var ids = [];
  list.forEach(function (id) {
    if (Number.isInteger(id) && id > 0 && ids.indexOf(id) === -1 && ids.length < RECENT_VIEWS_MAX) {
      ids.push(id);
    }
  });
  return ids;
}

// 저장 차단 · 개인 정보 보호 모드면 localStorage 읽기 · 쓰기가 오류 → 읽기 null, 쓰기 false (화면은 이번 접속 동안만 기억)
function loadRecentViews(storage) {
  try {
    return parseRecentViews(storage.getItem(RECENT_VIEWS_KEY));
  } catch (e) {
    return null;
  }
}

function saveRecentViews(storage, ids) {
  try {
    storage.setItem(RECENT_VIEWS_KEY, JSON.stringify(ids));
    return true;
  } catch (e) {
    return false;
  }
}

// 최근 본 탭이 비었을 때 안내 (favorite-ui.js favoriteEmptyState 와 같은 모양)
// recentCount = 지금 받아 온 행사 중 최근 본 개수 (0 보다 크면 검색어 · 날짜 · 지난 행사 조건에 걸러진 것)
function recentEmptyState(recentCount) {
  if (recentCount === 0) {
    return {
      title: "아직 본 행사가 없어요",
      body: "행사를 열어 보면 여기에 최근 본 순서로 모여요",
      actionLabel: "행사 둘러보기",
      action: "browse",
    };
  }
  return {
    title: "조건에 맞는 최근 본 행사가 없어요",
    body: "검색어 · 날짜 · 지난 행사 설정을 확인해 보세요",
    actionLabel: null,
    action: null,
  };
}

if (typeof module !== "undefined") {
  module.exports = {
    RECENT_VIEWS_KEY: RECENT_VIEWS_KEY,
    RECENT_VIEWS_MAX: RECENT_VIEWS_MAX,
    addRecentView: addRecentView,
    parseRecentViews: parseRecentViews,
    loadRecentViews: loadRecentViews,
    saveRecentViews: saveRecentViews,
    recentEmptyState: recentEmptyState,
  };
}
