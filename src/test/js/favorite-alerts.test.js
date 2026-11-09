// 찜 알림 규칙 시험 — 찜한 행사가 내일 시작 · 3일 안에 종료면 알림, 본 알림은 기기에만
const test = require("node:test");
const assert = require("node:assert");
const {
  FAV_ALERT_START_DAYS,
  FAV_ALERT_END_DAYS,
  favoriteAlertOf,
  favoriteAlerts,
  favoriteAlertLabel,
  favoriteAlertKey,
  parseSeenAlertKeys,
  unseenAlertCount,
  createAlertSeenStore,
} = require("../../main/resources/static/favorite-alerts.js");

// 한국 시간 그날 h 시
function kst(isoDate, h) {
  const [y, m, d] = isoDate.split("-").map(Number);
  return Date.UTC(y, m - 1, d, (h || 12) - 9);
}
const NOW = kst("2026-11-09");

function ev(id, title, startDate, endDate) {
  return { id, title, startDate, endDate };
}

test("기준: 시작 D-1 · 종료 D-3", () => {
  assert.strictEqual(FAV_ALERT_START_DAYS, 1);
  assert.strictEqual(FAV_ALERT_END_DAYS, 3);
});

test("내일 시작이면 starting, 모레부터는 알림 없음", () => {
  const a = favoriteAlertOf(ev(1, "가", "2026-11-10", "2026-11-20"), NOW);
  assert.strictEqual(a.kind, "starting");
  assert.strictEqual(a.days, 1);
  assert.strictEqual(favoriteAlertLabel(a), "내일 시작");
  assert.strictEqual(favoriteAlertOf(ev(2, "나", "2026-11-11", "2026-11-20"), NOW), null);
});

test("운영중이고 종료까지 0~3일이면 ending, 4일부터는 없음", () => {
  const today = favoriteAlertOf(ev(1, "가", "2026-11-01", "2026-11-09"), NOW);
  assert.strictEqual(favoriteAlertLabel(today), "오늘 종료");
  const three = favoriteAlertOf(ev(2, "나", "2026-11-01", "2026-11-12"), NOW);
  assert.strictEqual(favoriteAlertLabel(three), "3일 뒤 종료");
  assert.strictEqual(favoriteAlertOf(ev(3, "다", "2026-11-01", "2026-11-13"), NOW), null);
});

test("종료 · 날짜 없음 · 하루짜리 오늘 행사", () => {
  assert.strictEqual(favoriteAlertOf(ev(1, "가", "2026-11-01", "2026-11-08"), NOW), null);
  assert.strictEqual(favoriteAlertOf(ev(2, "나", null, null), NOW), null);
  // 끝 날짜가 없으면 시작일 = 종료일 → 오늘 하루 행사는 오늘 종료
  assert.strictEqual(favoriteAlertLabel(favoriteAlertOf(ev(3, "다", "2026-11-09", null), NOW)), "오늘 종료");
});

test("한국 시간 자정 기준 (UTC 로는 전날인 오전 8시도 오늘)", () => {
  const early = kst("2026-11-09", 8);
  assert.strictEqual(favoriteAlertOf(ev(1, "가", "2026-11-10", "2026-11-10"), early).kind, "starting");
});

test("찜한 행사만, 남은 날 적은 순 → 같으면 종료 먼저 → 제목", () => {
  const events = [
    ev(1, "나", "2026-11-10", "2026-11-30"), // 내일 시작 (1일)
    ev(2, "가", "2026-11-01", "2026-11-10"), // 1일 뒤 종료
    ev(3, "다", "2026-11-01", "2026-11-09"), // 오늘 종료
    ev(4, "라", "2026-11-01", "2026-11-09"), // 찜 아님
    ev(5, "마", "2026-11-01", "2026-12-31"), // 알림 없음
  ];
  const alerts = favoriteAlerts(events, new Set([1, 2, 3, 5]), NOW);
  assert.deepStrictEqual(
    alerts.map((a) => a.event.id),
    [3, 2, 1],
  );
});

test("본 알림 열쇠 = 번호:종류:날짜, 저장 글이 깨졌으면 빈 목록", () => {
  const a = favoriteAlertOf(ev(7, "가", "2026-11-10", "2026-11-20"), NOW);
  assert.strictEqual(favoriteAlertKey(a), "7:starting:2026-11-10");
  assert.deepStrictEqual(parseSeenAlertKeys("not json"), []);
  assert.deepStrictEqual(parseSeenAlertKeys('{"a":1}'), []);
  assert.deepStrictEqual(parseSeenAlertKeys('["7:starting:2026-11-10", 3, "x", "0:ending:2026-11-10"]'), [
    "7:starting:2026-11-10",
  ]);
});

test("안 본 알림 수: 날짜가 바뀌면 다시 새 알림", () => {
  const alerts = favoriteAlerts([ev(1, "가", "2026-11-01", "2026-11-10")], new Set([1]), NOW);
  assert.strictEqual(unseenAlertCount(alerts, []), 1);
  assert.strictEqual(unseenAlertCount(alerts, ["1:ending:2026-11-10"]), 0);
  assert.strictEqual(unseenAlertCount(alerts, ["1:ending:2026-11-09"]), 1);
});

function fakeStorage() {
  const data = {};
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => (data[k] = String(v)),
    removeItem: (k) => delete data[k],
  };
}

test("저장소: 지금 알림으로 통째로 바꿈 (지난 알림이 쌓이지 않음), 비면 지움", () => {
  const storage = fakeStorage();
  const store = createAlertSeenStore(storage, "k");
  const alerts = favoriteAlerts([ev(1, "가", "2026-11-01", "2026-11-10")], new Set([1]), NOW);
  store.markSeen(alerts);
  assert.deepStrictEqual(store.list(), ["1:ending:2026-11-10"]);
  store.markSeen([]);
  assert.strictEqual("k" in storage.data, false);
  assert.deepStrictEqual(store.list(), []);
});

test("저장소를 못 쓰면 이 페이지 메모리로", () => {
  const broken = {
    getItem() {
      throw new Error("blocked");
    },
    setItem() {
      throw new Error("blocked");
    },
    removeItem() {},
  };
  const alerts = favoriteAlerts([ev(1, "가", "2026-11-01", "2026-11-10")], new Set([1]), NOW);
  for (const storage of [null, broken]) {
    const store = createAlertSeenStore(storage, "k");
    store.markSeen(alerts);
    assert.deepStrictEqual(store.list(), ["1:ending:2026-11-10"]);
  }
});
