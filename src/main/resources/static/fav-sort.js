// 찜한 행사 정렬 규칙 (확정 시안 FavSortC, docs/design/2026-11-13-todo-additions.md Q1)
// 찜 구분 제목 오른쪽 [날짜순 | 이름순]. 기본 = 날짜순, 고른 값은 이 기기 브라우저 저장소(localStorage)에만 — 서버로 보내지 않음
// 화면(단추 · 목록)은 index.js, 여기는 비교 · 정렬 · 저장소만 다룸. 시험: node --test src/test/js/ (CI 에서 실행)

var FAV_SORT_DEFAULT = "date";
var FAV_SORT_OPTIONS = [
  { value: "date", label: "날짜순" },
  { value: "name", label: "이름순" },
];

// 시작일 오름차순 비교 (날짜 없는 행사는 뒤로) — "전체 행사 (날짜순)" · 같은 장소 카드 번호와 같은 규칙
function compareStartDate(a, b) {
  var da = a.event.startDate;
  var db = b.event.startDate;
  if (!da && !db) return 0;
  if (!da) return 1;
  if (!db) return -1;
  return da.localeCompare(db);
}

// 이름(제목) 가나다순
function compareTitle(a, b) {
  return (a.event.title || "").localeCompare(b.event.title || "", "ko");
}

// 저장해 둔 값 → "date" | "name". 없거나 틀린 값이면 기본(날짜순)
function normalizeFavSort(value) {
  return value === "name" ? "name" : FAV_SORT_DEFAULT;
}

// 찜 목록 정렬 (원본은 그대로 두고 새 배열). 날짜순에서 같은 날 시작이면 이름순으로 → 그릴 때마다 순서가 같음
function sortFavorites(items, mode) {
  var byName = normalizeFavSort(mode) === "name";
  return items.slice().sort(function (a, b) {
    if (byName) return compareTitle(a, b);
    return compareStartDate(a, b) || compareTitle(a, b);
  });
}

// 고른 정렬 기억. 저장소를 못 쓰면(막힘 · 가득 참 · 접근 오류) 이 페이지에서만 기억하고 처음엔 날짜순
function createFavSortStore(storage, key) {
  var memory = null;
  return {
    get: function () {
      if (memory) return memory;
      try {
        return normalizeFavSort(storage ? storage.getItem(key) : null);
      } catch (e) {
        return FAV_SORT_DEFAULT;
      }
    },
    set: function (mode) {
      var value = normalizeFavSort(mode);
      memory = value;
      try {
        if (storage) storage.setItem(key, value);
      } catch (e) {
        // 저장 실패 → 이 페이지에서만 (memory)
      }
      return value;
    },
  };
}

if (typeof module !== "undefined") {
  module.exports = {
    FAV_SORT_DEFAULT: FAV_SORT_DEFAULT,
    FAV_SORT_OPTIONS: FAV_SORT_OPTIONS,
    compareStartDate: compareStartDate,
    compareTitle: compareTitle,
    normalizeFavSort: normalizeFavSort,
    sortFavorites: sortFavorites,
    createFavSortStore: createFavSortStore,
  };
}
