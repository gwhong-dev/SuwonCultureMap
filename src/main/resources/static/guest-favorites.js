// 비회원 찜: 로그인 전에 ♡ 를 누른 행사 번호만 이 기기 브라우저 저장소(localStorage)에 (최대 100, 서버로 안 보냄)
// 로그인하면 index.js 가 POST /api/favorites/merge 로 계정 찜에 합치고 이 목록을 비움
// 시험: node --test src/test/js/ (CI 에서 실행)

var GUEST_FAVORITE_MAX = 100; // 서버 FavoriteService.MERGE_MAX 와 같음

function isGuestEventId(id) {
  return typeof id === "number" && Number.isInteger(id) && id > 0;
}

// 저장된 글 → 행사 번호 목록 (깨졌거나 이상한 값은 버림, 겹침 없음, 최대 개수까지)
function parseGuestFavorites(raw) {
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
      if (!isGuestEventId(id) || seen.has(id)) return false;
      seen.add(id);
      return true;
    })
    .slice(0, GUEST_FAVORITE_MAX);
}

// 있으면 빼고 없으면 맨 뒤에 더함. 가득 찼으면 더하지 않음 (added = null)
function toggleGuestFavoriteId(ids, id) {
  if (!isGuestEventId(id)) return { ids: ids, added: null };
  if (ids.indexOf(id) !== -1) {
    return {
      ids: ids.filter(function (x) {
        return x !== id;
      }),
      added: false,
    };
  }
  if (ids.length >= GUEST_FAVORITE_MAX) return { ids: ids, added: null };
  return { ids: ids.concat([id]), added: true };
}

// 불러온 행사(/api/events)에 없는 번호(삭제 · 숨김 · 다른 행사에 합쳐짐)를 뺌 → 화면에서 뺄 수 없는 찜이 100개 한도를 차지하지 않게
// 행사 목록이 비었으면(받기 이상) 아무것도 빼지 않음
function pruneGuestFavoriteIds(ids, knownIds) {
  if (!knownIds || !knownIds.size) return ids;
  return ids.filter(function (id) {
    return knownIds.has(id);
  });
}

// 저장소가 막힌 브라우저(사생활 보호 · 가득 참)에선 이 페이지에서만 기억 (최근 본 createRecentStore 와 같은 방식)
function createGuestFavoriteStore(storage, key) {
  var memory = [];
  var usable = !!storage;

  function list() {
    if (usable) {
      try {
        var raw = storage.getItem(key);
        return raw === null ? [] : parseGuestFavorites(raw);
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
      usable = false;
    }
  }

  return {
    list: list,
    // 결과: true 담음 · false 뺌 · null 못 담음(가득 참)
    toggle: function (id) {
      var result = toggleGuestFavoriteId(list(), id);
      if (result.added !== null) write(result.ids);
      return result.added;
    },
    clear: function () {
      write([]);
    },
    // 결과: 뺀 개수 (0 이면 저장소를 건드리지 않음)
    prune: function (knownIds) {
      var ids = list();
      var kept = pruneGuestFavoriteIds(ids, knownIds);
      if (kept.length !== ids.length) write(kept);
      return ids.length - kept.length;
    },
  };
}

// 비회원 찜 토스트: 담으면 "보기"(→ 찜 탭), 가득 차면 로그인 안내
function guestFavoriteToast(added) {
  if (added === null) {
    return { text: "이 기기엔 " + GUEST_FAVORITE_MAX + "개까지 찜할 수 있어요", actionLabel: "로그인" };
  }
  return added
    ? { text: "이 기기에 찜했어요 · 로그인하면 계정에 합쳐져요", actionLabel: "보기" }
    : { text: "찜을 해제했어요", actionLabel: null };
}

// 로그인 뒤 합친 결과 토스트 (더한 게 없으면 null → 토스트 없음)
function guestMergeToastText(added) {
  return added > 0 ? "이 기기에서 찜한 행사 " + added + "개를 계정에 합쳤어요" : null;
}

if (typeof module !== "undefined") {
  module.exports = {
    GUEST_FAVORITE_MAX: GUEST_FAVORITE_MAX,
    parseGuestFavorites: parseGuestFavorites,
    toggleGuestFavoriteId: toggleGuestFavoriteId,
    pruneGuestFavoriteIds: pruneGuestFavoriteIds,
    createGuestFavoriteStore: createGuestFavoriteStore,
    guestFavoriteToast: guestFavoriteToast,
    guestMergeToastText: guestMergeToastText,
  };
}
