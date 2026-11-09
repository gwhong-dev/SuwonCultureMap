// 찜 탭 "최근 본" 규칙 시험 — 브라우저 저장소에 행사 번호만, 최근 순 · 최대 20개
const test = require("node:test");
const assert = require("node:assert");
const {
  RECENT_MAX,
  parseRecentIds,
  addRecentId,
  pickRecent,
  createRecentStore,
  recentEmptyState,
} = require("../../main/resources/static/recent-views.js");

test("저장 글 읽기: 번호 배열이면 그대로", () => {
  assert.deepStrictEqual(parseRecentIds("[3,1,2]"), [3, 1, 2]);
});

test("저장 글 읽기: 깨졌거나 배열이 아니면 빈 목록 (화면이 멈추지 않게)", () => {
  assert.deepStrictEqual(parseRecentIds("not json"), []);
  assert.deepStrictEqual(parseRecentIds('{"a":1}'), []);
  assert.deepStrictEqual(parseRecentIds("null"), []);
  assert.deepStrictEqual(parseRecentIds(""), []);
});

test("저장 글 읽기: 번호가 아닌 값 · 겹친 번호는 빼고 최대 20개", () => {
  assert.deepStrictEqual(parseRecentIds('[5,"7",0,-1,2.5,null,5,9]'), [5, 9]);
  const many = JSON.stringify(Array.from({ length: 30 }, (_, i) => i + 1));
  assert.strictEqual(parseRecentIds(many).length, RECENT_MAX);
  assert.strictEqual(RECENT_MAX, 20);
});

test("새로 본 행사는 맨 앞, 이미 있으면 맨 앞으로 옮김 (겹치지 않게)", () => {
  assert.deepStrictEqual(addRecentId([], 4), [4]);
  assert.deepStrictEqual(addRecentId([1, 2, 3], 4), [4, 1, 2, 3]);
  assert.deepStrictEqual(addRecentId([1, 2, 3], 3), [3, 1, 2]);
  assert.deepStrictEqual(addRecentId([1, 2, 3], 1), [1, 2, 3]);
});

test("20개가 넘으면 가장 오래 전에 본 것부터 밀려남", () => {
  const full = Array.from({ length: RECENT_MAX }, (_, i) => i + 1); // 1 이 가장 최근, 20 이 가장 오래 전
  const next = addRecentId(full, 99);
  assert.strictEqual(next.length, RECENT_MAX);
  assert.strictEqual(next[0], 99);
  assert.ok(!next.includes(20));
  assert.deepStrictEqual(addRecentId([1, 2, 3], 4, 2), [4, 1]); // 개수 한도 직접 지정
});

test("번호가 아닌 값은 기록하지 않음 (원래 목록은 그대로 · 새 배열)", () => {
  const ids = [1, 2];
  const next = addRecentId(ids, undefined);
  assert.deepStrictEqual(next, [1, 2]);
  assert.notStrictEqual(next, ids);
  assert.deepStrictEqual(addRecentId(ids, "3"), [1, 2]);
});

test("행사 고르기: 저장한 순서대로, 지금 없는 행사(지워짐)는 건너뜀", () => {
  const events = [{ id: 1, title: "가" }, { id: 2, title: "나" }, { id: 3, title: "다" }];
  assert.deepStrictEqual(
    pickRecent([3, 9, 1], events).map((e) => e.title),
    ["다", "가"],
  );
  assert.deepStrictEqual(pickRecent([], events), []);
});

test("행사 고르기: 목록 항목에서 번호 꺼내는 법을 줄 수 있음 (지도 마커 항목 { event })", () => {
  const items = [{ event: { id: 7 } }, { event: { id: 8 } }];
  const picked = pickRecent([8, 7], items, (item) => item.event.id);
  assert.deepStrictEqual(
    picked.map((item) => item.event.id),
    [8, 7],
  );
  assert.strictEqual(picked[0], items[1]); // 같은 항목을 그대로 (복사 안 함)
});

// 가짜 저장소 (localStorage 와 같은 getItem · setItem · removeItem)
function fakeStorage() {
  const data = new Map();
  return {
    data,
    getItem: (k) => (data.has(k) ? data.get(k) : null),
    setItem: (k, v) => data.set(k, String(v)),
    removeItem: (k) => data.delete(k),
  };
}

test("저장소: 본 순서대로 저장 · 읽기, 모두 지우면 키를 지움", () => {
  const storage = fakeStorage();
  const store = createRecentStore(storage, "k");
  assert.deepStrictEqual(store.list(), []);
  store.add(1);
  store.add(2);
  assert.deepStrictEqual(store.add(1), [1, 2]);
  assert.strictEqual(storage.getItem("k"), "[1,2]");
  store.clear();
  assert.strictEqual(storage.getItem("k"), null);
  assert.deepStrictEqual(store.list(), []);
});

test("저장소: 다른 창에서 지운 기록을 이 창이 되살리지 않음 (#158 검증 ⚠️)", () => {
  const storage = fakeStorage();
  const store = createRecentStore(storage, "k");
  store.add(1);
  store.add(2);
  storage.removeItem("k"); // 다른 창의 "모두 지우기" · 사이트 데이터 삭제
  assert.deepStrictEqual(store.list(), []);
  assert.deepStrictEqual(store.add(9), [9]);
});

test("저장소: 다른 창에서 본 것도 같이 보임", () => {
  const storage = fakeStorage();
  const a = createRecentStore(storage, "k");
  const b = createRecentStore(storage, "k");
  a.add(1);
  assert.deepStrictEqual(b.add(2), [2, 1]);
  assert.deepStrictEqual(a.list(), [2, 1]);
});

test("저장소가 막혔으면(접근 오류 · 없음) 이 페이지 메모리로 기억 (화면이 멈추지 않게)", () => {
  const blocked = {
    getItem() {
      throw new Error("SecurityError");
    },
    setItem() {
      throw new Error("SecurityError");
    },
    removeItem() {
      throw new Error("SecurityError");
    },
  };
  for (const storage of [blocked, null]) {
    const store = createRecentStore(storage, "k");
    assert.deepStrictEqual(store.list(), []);
    store.add(3);
    assert.deepStrictEqual(store.add(4), [4, 3]);
    store.clear();
    assert.deepStrictEqual(store.list(), []);
  }
});

test("저장소 쓰기만 실패하면(가득 참) 그 뒤로는 메모리로 이어서 기억", () => {
  const storage = fakeStorage();
  storage.setItem("k", "[5]");
  storage.setItem = () => {
    throw new Error("QuotaExceededError");
  };
  const store = createRecentStore(storage, "k");
  assert.deepStrictEqual(store.add(6), [6, 5]); // 읽기는 됐으니 이전 기록 + 새 것
  assert.deepStrictEqual(store.list(), [6, 5]); // 쓰기 실패 뒤엔 메모리 값
  assert.deepStrictEqual(store.add(7), [7, 6, 5]);
});

test("최근 본이 비었을 때: 안내 + 행사 둘러보기", () => {
  const state = recentEmptyState();
  assert.strictEqual(state.title, "아직 본 행사가 없어요");
  assert.strictEqual(state.action, "browse");
  assert.strictEqual(state.actionLabel, "행사 둘러보기");
});
