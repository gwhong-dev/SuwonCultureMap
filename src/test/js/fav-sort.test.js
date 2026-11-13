// 찜한 행사 정렬 [날짜순 | 이름순] 시험 (확정 시안 FavSortC) — 기본 날짜순, 고른 값은 이 기기 저장소
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  FAV_SORT_DEFAULT,
  FAV_SORT_OPTIONS,
  compareStartDate,
  normalizeFavSort,
  sortFavorites,
  createFavSortStore,
} = require("../../main/resources/static/fav-sort.js");

const item = (id, title, startDate) => ({ event: { id, title, startDate } });
const ids = (list) => list.map((x) => x.event.id);

const A = item(1, "다 행사", "2026-11-20");
const B = item(2, "가 행사", "2026-12-01");
const C = item(3, "나 행사", "2026-11-05");
const NO_DATE = item(4, "라 행사", null);

test("기본 = 날짜순, 단추는 [날짜순 | 이름순] 차례", () => {
  assert.strictEqual(FAV_SORT_DEFAULT, "date");
  assert.deepStrictEqual(
    FAV_SORT_OPTIONS.map((o) => o.label),
    ["날짜순", "이름순"],
  );
});

test("날짜순: 시작일 빠른 차례, 날짜 없는 행사는 뒤로", () => {
  assert.deepStrictEqual(ids(sortFavorites([NO_DATE, A, B, C], "date")), [3, 1, 2, 4]);
});

test("이름순: 가나다 차례 (지금 규칙 localeCompare ko)", () => {
  assert.deepStrictEqual(ids(sortFavorites([NO_DATE, A, B, C], "name")), [2, 3, 1, 4]);
});

test("날짜순에서 같은 날 시작이면 이름순 → 들어온 순서와 상관없이 같음", () => {
  const x = item(10, "하 같은 날", "2026-11-20");
  const y = item(11, "가 같은 날", "2026-11-20");
  assert.deepStrictEqual(ids(sortFavorites([x, y], "date")), [11, 10]);
  assert.deepStrictEqual(ids(sortFavorites([y, x], "date")), [11, 10]);
});

test("원본 배열은 그대로 둠", () => {
  const list = [B, A];
  sortFavorites(list, "date");
  assert.deepStrictEqual(ids(list), [2, 1]);
});

test("틀린 값 · 없는 값은 날짜순", () => {
  assert.strictEqual(normalizeFavSort("name"), "name");
  assert.strictEqual(normalizeFavSort("date"), "date");
  assert.strictEqual(normalizeFavSort(null), "date");
  assert.strictEqual(normalizeFavSort("title"), "date");
  assert.deepStrictEqual(ids(sortFavorites([B, C], "엉뚱한 값")), [3, 2]);
});

test("compareStartDate: 전체 행사 (날짜순)과 같은 규칙", () => {
  assert.ok(compareStartDate(C, A) < 0);
  assert.ok(compareStartDate(NO_DATE, A) > 0);
  assert.strictEqual(compareStartDate(NO_DATE, item(5, "x", "")), 0);
});

function memoryStorage(initial) {
  const data = Object.assign({}, initial);
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => {
      data[k] = String(v);
    },
  };
}

test("기억: 고른 값을 저장하고 다음에 읽음", () => {
  const storage = memoryStorage();
  const store = createFavSortStore(storage, "suwonbible.favSort");
  assert.strictEqual(store.get(), "date");
  store.set("name");
  assert.strictEqual(storage.data["suwonbible.favSort"], "name");
  assert.strictEqual(createFavSortStore(storage, "suwonbible.favSort").get(), "name");
});

test("기억: 저장된 값이 틀렸으면 날짜순", () => {
  const store = createFavSortStore(memoryStorage({ k: "<script>" }), "k");
  assert.strictEqual(store.get(), "date");
});

test("기억 실패: 저장소가 없거나 읽기 · 쓰기가 오류여도 날짜순으로 동작, 고른 값은 이 페이지에서만", () => {
  const broken = {
    getItem: () => {
      throw new Error("SecurityError");
    },
    setItem: () => {
      throw new Error("QuotaExceededError");
    },
  };
  for (const storage of [null, broken]) {
    const store = createFavSortStore(storage, "k");
    assert.strictEqual(store.get(), "date");
    assert.strictEqual(store.set("name"), "name");
    assert.strictEqual(store.get(), "name");
  }
});

// 화면 연결 (index.js): 정렬 단추 묶음에 role=group · aria-pressed, 저장 키
test("index.js: 찜 구분 제목에 정렬 단추 묶음 (role=group · aria-pressed) · 기억 키", () => {
  const js = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/js/index.js"),
    "utf8",
  );
  assert.match(js, /role="group" aria-label="찜한 행사 정렬"/);
  assert.match(js, /aria-pressed="/);
  assert.match(js, /"suwonbible\.favSort"/);
  assert.doesNotMatch(js, /\(이름순\)/, "옛 고정 제목 '(이름순)' 이 남아 있음");
});
