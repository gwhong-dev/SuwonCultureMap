// 비회원 찜 시험 — 이 기기 브라우저 저장소에 행사 번호만, 최대 100, 로그인하면 합치고 비움
const test = require("node:test");
const assert = require("node:assert");
const {
  GUEST_FAVORITE_MAX,
  parseGuestFavorites,
  toggleGuestFavoriteId,
  evictableGuestFavoriteId,
  createGuestFavoriteStore,
  guestFavoriteToast,
  guestMergeToastText,
} = require("../../main/resources/static/guest-favorites.js");

function fakeStorage() {
  const data = {};
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => {
      data[k] = String(v);
    },
    removeItem: (k) => {
      delete data[k];
    },
  };
}

test("parseGuestFavorites: 깨진 글 · 이상한 값은 버리고 겹침 없이 최대 개수까지", () => {
  assert.deepStrictEqual(parseGuestFavorites("[3,1,3,0,-2,1.5,\"7\",null,9]"), [3, 1, 9]);
  assert.deepStrictEqual(parseGuestFavorites("{}"), []);
  assert.deepStrictEqual(parseGuestFavorites("깨짐"), []);
  const many = JSON.stringify(Array.from({ length: 150 }, (_, i) => i + 1));
  assert.strictEqual(parseGuestFavorites(many).length, GUEST_FAVORITE_MAX);
});

test("toggleGuestFavoriteId: 없으면 맨 뒤에 담고 있으면 뺌, 가득 차면 못 담음(null)", () => {
  assert.deepStrictEqual(toggleGuestFavoriteId([1, 2], 3), { ids: [1, 2, 3], added: true });
  assert.deepStrictEqual(toggleGuestFavoriteId([1, 2, 3], 2), { ids: [1, 3], added: false });
  const full = Array.from({ length: GUEST_FAVORITE_MAX }, (_, i) => i + 1);
  assert.strictEqual(toggleGuestFavoriteId(full, 999).added, null);
  assert.strictEqual(toggleGuestFavoriteId(full, 1).added, false); // 가득 차도 빼기는 됨
  assert.strictEqual(toggleGuestFavoriteId([], 0).added, null);
});

test("createGuestFavoriteStore: 저장소에 쓰고 비우면 키를 지움", () => {
  const storage = fakeStorage();
  const store = createGuestFavoriteStore(storage, "k");
  assert.strictEqual(store.toggle(5), true);
  assert.strictEqual(store.toggle(7), true);
  assert.strictEqual(storage.data.k, "[5,7]");
  assert.strictEqual(store.toggle(5), false);
  assert.deepStrictEqual(store.list(), [7]);
  store.clear();
  assert.strictEqual("k" in storage.data, false);
  assert.deepStrictEqual(store.list(), []);
});

test("createGuestFavoriteStore: 저장소가 없거나 쓰기가 막히면 이 페이지 메모리로", () => {
  const none = createGuestFavoriteStore(null, "k");
  none.toggle(3);
  assert.deepStrictEqual(none.list(), [3]);

  const broken = fakeStorage();
  broken.setItem = () => {
    throw new Error("QuotaExceededError");
  };
  const store = createGuestFavoriteStore(broken, "k");
  assert.strictEqual(store.toggle(4), true);
  assert.deepStrictEqual(store.list(), [4]);
});

test("다른 창에서 지운 목록이 되살아나지 않음 (저장소가 되면 늘 저장소 기준)", () => {
  const storage = fakeStorage();
  const a = createGuestFavoriteStore(storage, "k");
  const b = createGuestFavoriteStore(storage, "k");
  a.toggle(1);
  b.clear();
  assert.deepStrictEqual(a.list(), []);
});

test("토스트: 담음 → 보기 + 합치기 안내, 뺌 → 글만, 가득 참 → 로그인, 합친 개수 0 이면 토스트 없음", () => {
  assert.deepStrictEqual(guestFavoriteToast(true), {
    text: "이 기기에 찜했어요 · 로그인하면 계정에 합쳐져요",
    actionLabel: "보기",
  });
  assert.deepStrictEqual(guestFavoriteToast(false), { text: "찜을 해제했어요", actionLabel: null });
  assert.strictEqual(guestFavoriteToast(null).actionLabel, "로그인");
  assert.strictEqual(guestMergeToastText(2), "이 기기에서 찜한 행사 2개를 계정에 합쳤어요");
  assert.strictEqual(guestMergeToastText(0), null);
});

test("evictableGuestFavoriteId: 화면에 없는 번호 중 가장 오래된 것, 없거나 행사 목록을 모르면 null", () => {
  assert.strictEqual(evictableGuestFavoriteId([5, 2, 9, 4], new Set([5, 9])), 2);
  assert.strictEqual(evictableGuestFavoriteId([5, 9], new Set([5, 9])), null);
  assert.strictEqual(evictableGuestFavoriteId([5, 2], new Set()), null);
  assert.strictEqual(evictableGuestFavoriteId([5, 2], null), null);
});

test("가득 찼을 때: 화면에 없는 번호(합쳐 숨김 등)를 하나 밀어내고 담음, 밀어낼 게 없으면 못 담음", () => {
  const full = Array.from({ length: GUEST_FAVORITE_MAX }, (_, i) => i + 1);
  const known = new Set(full.filter((id) => id !== 3 && id !== 7));
  const r = toggleGuestFavoriteId(full, 500, known);
  assert.strictEqual(r.added, true);
  assert.strictEqual(r.ids.length, GUEST_FAVORITE_MAX);
  assert.ok(!r.ids.includes(3) && r.ids.includes(7) && r.ids[r.ids.length - 1] === 500);
  assert.strictEqual(toggleGuestFavoriteId(full, 500, new Set(full)).added, null);
  assert.strictEqual(toggleGuestFavoriteId(full, 500).added, null);
});

test("store.setKnownIds: 화면에 없는 번호를 지우지 않음(로그인 때 서버가 대표로 옮기게), 가득 찼을 때만 밀어냄", () => {
  const storage = fakeStorage();
  const store = createGuestFavoriteStore(storage, "k");
  store.toggle(1);
  store.toggle(2);
  store.setKnownIds(new Set([2]));
  assert.deepStrictEqual(store.list(), [1, 2]);
  assert.strictEqual(storage.data.k, "[1,2]");

  for (let id = 3; id <= GUEST_FAVORITE_MAX; id++) store.toggle(id);
  assert.strictEqual(store.list().length, GUEST_FAVORITE_MAX);
  assert.strictEqual(store.toggle(1000), true); // 화면에 없는 1 을 밀어냄
  assert.deepStrictEqual(store.list().slice(0, 2), [2, 3]);
  assert.strictEqual(store.list()[GUEST_FAVORITE_MAX - 1], 1000);
});
