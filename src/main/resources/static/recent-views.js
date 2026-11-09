// 찜 탭 "최근 본" 규칙 (docs/design/mobile-design-v1.md 05 "찜 N | 최근 본 N")
// 상세를 연 행사 번호만 이 기기의 브라우저 저장소(localStorage)에 최근 순으로 둠 — 서버로 보내지 않음, 비로그인도 사용
// 저장소 읽기 · 쓰기는 index.js, 여기는 값만 다룸. 시험: node --test src/test/js/ (CI 에서 실행)

var RECENT_MAX = 20; // 오래된 것부터 밀려남

function isEventId(value) {
  return typeof value === "number" && Number.isInteger(value) && value > 0;
}

// 저장해 둔 글 → 행사 번호 목록. 깨졌거나 남이 넣은 값이면 맞는 번호만 (전부 틀리면 빈 목록)
function parseRecentIds(raw) {
  var list;
  try {
    list = JSON.parse(raw);
  } catch (e) {
    return [];
  }
  if (!Array.isArray(list)) return [];
  var seen = new Set();
  return list
    .filter(function (id) {
      if (!isEventId(id) || seen.has(id)) return false;
      seen.add(id);
      return true;
    })
    .slice(0, RECENT_MAX);
}

// 새로 본 행사를 맨 앞에 (이미 있으면 그 자리에서 빼서 맨 앞으로), 최대 max 개
function addRecentId(ids, id, max) {
  if (!isEventId(id)) return ids.slice();
  return [id]
    .concat(
      ids.filter(function (other) {
        return other !== id;
      }),
    )
    .slice(0, max || RECENT_MAX);
}

// 번호 순서대로 지금 받아 온 행사를 고름. 사라진 행사(동기화로 지워짐)는 건너뜀
// getId: 목록 항목에서 번호를 꺼내는 법 (기본 = item.id)
function pickRecent(ids, items, getId) {
  var idOf =
    getId ||
    function (item) {
      return item.id;
    };
  var byId = new Map();
  items.forEach(function (item) {
    byId.set(idOf(item), item);
  });
  return ids
    .filter(function (id) {
      return byId.has(id);
    })
    .map(function (id) {
      return byId.get(id);
    });
}

// "최근 본" 이 비었을 때 안내 (action: "browse" = 전체 행사로)
function recentEmptyState() {
  return {
    title: "아직 본 행사가 없어요",
    body: "행사 상세를 열면 여기에 최근 순으로 모여요",
    actionLabel: "행사 둘러보기",
    action: "browse",
  };
}

if (typeof module !== "undefined") {
  module.exports = {
    RECENT_MAX: RECENT_MAX,
    parseRecentIds: parseRecentIds,
    addRecentId: addRecentId,
    pickRecent: pickRecent,
    recentEmptyState: recentEmptyState,
  };
}
