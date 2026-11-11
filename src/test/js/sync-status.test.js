// 데이터 갱신 시각 줄 시험 — "M/D HH:mm 갱신", 실패 · 값 없음이면 줄을 숨김
const test = require("node:test");
const assert = require("node:assert");
const { syncStatusText, showSyncStatus, loadSyncStatus } = require("../../main/resources/static/sync-status.js");

function fakeEl() {
  return { textContent: "옛 글", hidden: false };
}

function fakeFetch(answer) {
  const calls = [];
  const fn = (url) => {
    calls.push(url);
    if (answer instanceof Error) return Promise.reject(answer);
    return Promise.resolve(answer);
  };
  fn.calls = calls;
  return fn;
}

function ok(body) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}

test("한국 시간 값 → M/D HH:mm 갱신 (월 · 일 앞 0 없음, 시 · 분은 두 자리)", () => {
  assert.strictEqual(syncStatusText("2026-11-11T06:00:12"), "11/11 06:00 갱신");
  assert.strictEqual(syncStatusText("2026-01-05T18:07:00"), "1/5 18:07 갱신");
  assert.strictEqual(syncStatusText("2026-12-31T00:00"), "12/31 00:00 갱신");
});

test("기기 시간대와 상관없이 서버 값의 숫자 그대로 (UTC 로 바꾸지 않음)", () => {
  assert.strictEqual(syncStatusText("2026-11-11T06:00:00+09:00"), "11/11 06:00 갱신");
});

test("없음 · 틀린 형식이면 null (줄을 숨김)", () => {
  assert.strictEqual(syncStatusText(null), null);
  assert.strictEqual(syncStatusText(undefined), null);
  assert.strictEqual(syncStatusText(""), null);
  assert.strictEqual(syncStatusText("어제"), null);
  assert.strictEqual(syncStatusText(1731272400000), null);
  assert.strictEqual(syncStatusText("2026-13-01T06:00:00"), null);
  assert.strictEqual(syncStatusText("2026-11-00T06:00:00"), null);
  assert.strictEqual(syncStatusText("2026-11-11T24:00:00"), null);
});

test("칸 표시: 글이 있으면 보이고, 없으면 비우고 숨김", () => {
  const a = fakeEl();
  const b = fakeEl();
  showSyncStatus([a, b], "11/11 06:00 갱신");
  assert.deepStrictEqual([a, b].map((e) => [e.textContent, e.hidden]), [["11/11 06:00 갱신", false], ["11/11 06:00 갱신", false]]);
  showSyncStatus([a, b], null);
  assert.deepStrictEqual([a, b].map((e) => [e.textContent, e.hidden]), [["", true], ["", true]]);
});

test("API 성공 → 모바일 · PC 칸 모두 같은 글", async () => {
  const els = [fakeEl(), fakeEl()];
  const f = fakeFetch(ok({ lastSuccessAt: "2026-11-11T06:00:12", createdCount: 3, updatedCount: 7 }));
  const text = await loadSyncStatus(f, els);
  assert.strictEqual(text, "11/11 06:00 갱신");
  assert.deepStrictEqual(f.calls, ["/api/sync-status"]);
  assert.ok(els.every((e) => !e.hidden && e.textContent === "11/11 06:00 갱신"));
});

test("성공 기록이 없으면(lastSuccessAt null) 숨김", async () => {
  const els = [fakeEl()];
  assert.strictEqual(await loadSyncStatus(fakeFetch(ok({ lastSuccessAt: null, createdCount: null, updatedCount: null })), els), null);
  assert.strictEqual(els[0].hidden, true);
  assert.strictEqual(els[0].textContent, "");
});

test("API 실패(네트워크 · 500 · JSON 깨짐 · 빈 응답)면 숨김", async () => {
  const cases = [
    new Error("network"),
    { ok: false, status: 500, json: () => Promise.resolve({}) },
    { ok: true, status: 200, json: () => Promise.reject(new SyntaxError("bad json")) },
    ok(null),
  ];
  for (const answer of cases) {
    const els = [fakeEl()];
    assert.strictEqual(await loadSyncStatus(fakeFetch(answer), els), null);
    assert.strictEqual(els[0].hidden, true);
  }
});
