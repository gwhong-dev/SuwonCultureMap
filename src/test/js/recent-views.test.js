// 최근 본 행사 시험 — 상세를 연 행사 번호를 이 기기 브라우저(localStorage)에만 최근 순으로
const test = require("node:test");
const assert = require("node:assert");
const {
  RECENT_VIEWS_KEY,
  RECENT_VIEWS_MAX,
  addRecentView,
  parseRecentViews,
  loadRecentViews,
  saveRecentViews,
  recentEmptyState,
} = require("../../main/resources/static/recent-views.js");

// localStorage 흉내 (getItem · setItem 만)
function memoryStorage(initial) {
  const data = Object.assign({}, initial);
  return {
    getItem: (key) => (key in data ? data[key] : null),
    setItem: (key, value) => {
      data[key] = String(value);
    },
    data,
  };
}

// 사파리 저장 차단 · 용량 초과처럼 읽기 · 쓰기가 오류
const brokenStorage = {
  getItem: () => {
    throw new Error("SecurityError");
  },
  setItem: () => {
    throw new Error("QuotaExceededError");
  },
};

test("방금 본 행사가 맨 앞, 이미 있던 행사는 앞으로 옮김 (중복 없음)", () => {
  assert.deepStrictEqual(addRecentView([], 5), [5]);
  assert.deepStrictEqual(addRecentView([3, 2, 1], 4), [4, 3, 2, 1]);
  assert.deepStrictEqual(addRecentView([3, 2, 1], 2), [2, 3, 1]);
  assert.deepStrictEqual(addRecentView([3, 2, 1], 3), [3, 2, 1]);
});

test("최대 개수를 넘으면 가장 오래 본 행사부터 빠짐 · 원래 배열은 그대로", () => {
  const full = [];
  for (let i = 1; i <= RECENT_VIEWS_MAX; i++) full.push(i);
  const next = addRecentView(full, 999);
  assert.strictEqual(next.length, RECENT_VIEWS_MAX);
  assert.strictEqual(next[0], 999);
  assert.ok(!next.includes(RECENT_VIEWS_MAX)); // 맨 끝(가장 오래 본) 이 빠짐
  assert.strictEqual(full.length, RECENT_VIEWS_MAX); // 새 배열을 돌려줌
});

test("저장 글 읽기: 깨졌거나 손댄 값은 버림 (양의 정수만, 중복 없이, 최대 개수까지)", () => {
  assert.deepStrictEqual(parseRecentViews("[3,1,2]"), [3, 1, 2]);
  assert.deepStrictEqual(parseRecentViews(null), []); // 처음 (저장된 것 없음)
  assert.deepStrictEqual(parseRecentViews(""), []);
  assert.deepStrictEqual(parseRecentViews("{깨짐"), []);
  assert.deepStrictEqual(parseRecentViews('{"a":1}'), []); // 배열이 아님
  assert.deepStrictEqual(parseRecentViews('[3,"4",0,-1,1.5,null,3,7]'), [3, 7]);
  const many = [];
  for (let i = 1; i <= RECENT_VIEWS_MAX + 10; i++) many.push(i);
  assert.strictEqual(parseRecentViews(JSON.stringify(many)).length, RECENT_VIEWS_MAX);
});

test("저장소에 쓰고 다시 읽으면 같은 순서", () => {
  const storage = memoryStorage();
  assert.deepStrictEqual(loadRecentViews(storage), []); // 처음엔 빈 목록
  assert.strictEqual(saveRecentViews(storage, [9, 4, 7]), true);
  assert.strictEqual(storage.data[RECENT_VIEWS_KEY], "[9,4,7]");
  assert.deepStrictEqual(loadRecentViews(storage), [9, 4, 7]);
});

test("저장소를 못 쓰면(저장 차단 · 개인 정보 보호 모드) 오류 없이: 읽기 null · 쓰기 false", () => {
  assert.strictEqual(loadRecentViews(brokenStorage), null);
  assert.strictEqual(saveRecentViews(brokenStorage, [1]), false);
  assert.strictEqual(loadRecentViews(null), null); // window.localStorage 접근부터 막힌 경우
  assert.strictEqual(saveRecentViews(null, [1]), false);
});

test("빈 최근 본 탭: 본 행사가 없으면 행사 둘러보기 (로그인 필요 없음)", () => {
  const state = recentEmptyState(0);
  assert.strictEqual(state.title, "아직 본 행사가 없어요");
  assert.strictEqual(state.action, "browse");
  assert.strictEqual(state.actionLabel, "행사 둘러보기");
});

test("빈 최근 본 탭: 본 행사는 있는데 검색 · 날짜 · 지난 행사 조건에 걸러졌으면 조건 확인 안내 (버튼 없음)", () => {
  const state = recentEmptyState(3);
  assert.strictEqual(state.title, "조건에 맞는 최근 본 행사가 없어요");
  assert.ok(state.body.includes("지난 행사"));
  assert.strictEqual(state.action, null);
  assert.strictEqual(state.actionLabel, null);
});
