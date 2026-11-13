// 찜 탭 "최근 본" 규칙 (docs/design/mobile-design-v1.md 05 "찜 N | 최근 본 N")
// 상세를 연 행사 번호만 이 기기의 브라우저 저장소(localStorage)에 최근 순으로 둠 — 서버로 보내지 않음, 비로그인도 사용
// 화면(탭 줄 · 목록)은 index.js, 여기는 값 · 저장소만 다룸. 시험: node --test src/test/js/ (CI 에서 실행)

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

// 저장소 읽기 · 쓰기. storage = window.localStorage (시험에선 가짜), 못 쓰면 null
// 저장소가 막혔거나 쓰기에 실패하면 이 페이지에서만 메모리로 기억.
// 저장소가 되면 늘 저장소 값이 기준 → 다른 창에서 지운 기록을 이 창이 되살리지 않음
function createRecentStore(storage, key) {
  var memory = [];
  var usable = !!storage;

  function list() {
    if (usable) {
      try {
        var raw = storage.getItem(key);
        return raw === null ? [] : parseRecentIds(raw);
      } catch (e) {
        usable = false;
      }
    }
    return memory;
  }

  function write(ids) {
    memory = ids;
    if (!usable) return;
    try {
      if (ids.length) {
        storage.setItem(key, JSON.stringify(ids));
      } else {
        storage.removeItem(key);
      }
    } catch (e) {
      usable = false; // 가득 찼거나 막힘 → 이제부터 메모리
    }
  }

  return {
    list: list,
    add: function (id) {
      var next = addRecentId(list(), id);
      write(next);
      return next;
    },
    clear: function () {
      write([]);
    },
  };
}

// 화면 언어 (i18n.js, node 시험 = 한국어)
var recentI18n = typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n;

// "최근 본" 이 비었을 때 안내 (action: "browse" = 전체 행사로)
function recentEmptyState() {
  return {
    title: recentI18n.t("recent.emptyTitle"),
    body: recentI18n.t("recent.emptyBody"),
    actionLabel: recentI18n.t("fav.browse"),
    action: "browse",
  };
}

if (typeof module !== "undefined") {
  module.exports = {
    RECENT_MAX: RECENT_MAX,
    parseRecentIds: parseRecentIds,
    addRecentId: addRecentId,
    pickRecent: pickRecent,
    createRecentStore: createRecentStore,
    recentEmptyState: recentEmptyState,
  };
}
