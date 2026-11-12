// 재단 동기화 실패 알림 시험 — 판단(연속 실패 · 오래됨 · 이력 없음) + Issue 처리(중복 없음 · 복구 시 닫기), 가짜 fetch · gh
const test = require("node:test");
const assert = require("node:assert");
const { decide, parseKst, formatKst, main } = require("../../../deploy/sync-alert.js");

const H = 3600000;
// 2026-11-12 07:00 KST
const NOW = Date.parse("2026-11-12T07:00:00+09:00");

function status(lastSuccessAt, consecutiveFailures) {
  return { lastSuccessAt, createdCount: 0, updatedCount: 1, consecutiveFailures };
}

// ===== decide =====

test("마지막 성공이 25시간 안이고 실패 1회 이하면 ok", () => {
  assert.strictEqual(decide(status("2026-11-12T06:00:05", 0), NOW).state, "ok");
  assert.strictEqual(decide(status("2026-11-11T18:01:00", 1), NOW).state, "ok"); // 06시 1회 실패 = 아직 알리지 않음
});

test("연속 실패 2회면 alert", () => {
  const r = decide(status("2026-11-11T18:00:00", 2), NOW);
  assert.strictEqual(r.state, "alert");
  assert.match(r.reasons.join(), /2회 연속 실패/);
});

test("성공이 한 번도 없어도 연속 실패 2회면 alert", () => {
  assert.strictEqual(decide(status(null, 2), NOW).state, "alert");
});

test("마지막 성공이 25시간 넘으면 실패 횟수와 상관없이 alert (예약 실행이 아예 안 돈 경우)", () => {
  const r = decide(status("2026-11-11T05:59:00", 0), NOW); // 25시간 1분 전
  assert.strictEqual(r.state, "alert");
  assert.match(r.reasons.join(), /25\.0시간 전/);
  assert.strictEqual(decide(status("2026-11-11T06:00:00", 0), NOW).state, "ok"); // 딱 25시간 = 아직
});

// 실제 점검은 매시 17분 (sync-alert.yml) — 그 시각 기준 경계
test("06시 성공 → 18시 1회 실패: 19:17 · 다음 날 05:17 점검 모두 알리지 않음", () => {
  const at = (t) => Date.parse(t + "+09:00");
  const s = status("2026-11-11T06:02:00", 1);
  assert.strictEqual(decide(s, at("2026-11-11T19:17:00")).state, "ok");
  assert.strictEqual(decide(s, at("2026-11-12T05:17:00")).state, "ok");
});

test("06시 성공 → 18시 · 06시 2회 실패: 다음 날 06:17 점검에 alert", () => {
  const r = decide(status("2026-11-11T06:02:00", 2), Date.parse("2026-11-12T06:17:00+09:00"));
  assert.strictEqual(r.state, "alert");
  assert.deepStrictEqual(r.reasons, ["재단 동기화 2회 연속 실패"]);
});

test("예약 실행이 멈춤(FAIL 도 안 남음): 06시 성공 뒤 다음 날 07:17 점검에 alert", () => {
  const at = (t) => Date.parse(t + "+09:00");
  const s = status("2026-11-11T06:02:00", 0);
  assert.strictEqual(decide(s, at("2026-11-12T06:17:00")).state, "ok"); // 24.25시간
  assert.strictEqual(decide(s, at("2026-11-12T07:17:00")).state, "alert"); // 25.25시간
});

test("이력 없음(막 배포) · 실패 1회뿐 · 응답 없음 · 형식 이상은 unknown — 알리지도 닫지도 않음", () => {
  assert.strictEqual(decide(status(null, 0), NOW).state, "unknown");
  assert.strictEqual(decide(status(null, 1), NOW).state, "unknown");
  assert.strictEqual(decide(status(null, null), NOW).state, "unknown"); // 서버 조회 실패 = 모두 null
  assert.strictEqual(decide(null, NOW).state, "unknown");
  assert.strictEqual(decide([], NOW).state, "unknown");
  assert.strictEqual(decide(status("어제", 0), NOW).state, "unknown");
});

test("연속 실패 값이 없는 옛 서버면 시각만으로 판단", () => {
  assert.strictEqual(decide({ lastSuccessAt: "2026-11-12T06:00:00" }, NOW).state, "ok");
  assert.strictEqual(decide({ lastSuccessAt: "2026-11-10T06:00:00" }, NOW).state, "alert");
});

test("기준값은 바꿀 수 있음", () => {
  assert.strictEqual(decide(status("2026-11-12T06:00:00", 2), NOW, { failLimit: 3 }).state, "ok");
  assert.strictEqual(decide(status("2026-11-12T00:00:00", 0), NOW, { staleHours: 6 }).state, "alert");
});

test("한국 시간 읽기 · 쓰기", () => {
  assert.strictEqual(parseKst("2026-11-12T06:00:00"), Date.parse("2026-11-11T21:00:00Z"));
  assert.ok(Number.isNaN(parseKst("2026-11-12")));
  assert.strictEqual(formatKst(NOW), "2026-11-12 07:00 KST");
});

// ===== main: Issue 처리 =====

function fakeFetch(answers) {
  const queue = answers.slice();
  return async () => {
    const a = queue.length > 1 ? queue.shift() : queue[0];
    if (a instanceof Error) throw a;
    if (typeof a === "number") return { ok: false, status: a, json: async () => ({}) };
    return { ok: true, status: 200, json: async () => a };
  };
}

function fakeGh(openIssue, failOn) {
  const calls = [];
  const gh = (args) => {
    calls.push(args);
    if (failOn && args[0] === failOn[0] && args[1] === failOn[1]) throw new Error("gh 실패");
    if (args[0] === "issue" && args[1] === "list") return openIssue ? `${openIssue}\n` : "";
    return "";
  };
  gh.calls = calls;
  gh.kinds = () => calls.map((c) => c.slice(0, 2).join(" "));
  return gh;
}

function run(fetchAnswers, gh) {
  const logs = [];
  return main({
    fetchFn: fakeFetch(fetchAnswers),
    gh,
    env: { WAIT_MS: "0", GITHUB_REPOSITORY: "o/r", GITHUB_RUN_ID: "9" },
    nowMs: NOW,
    sleep: async () => {},
    log: (m) => logs.push(m),
  }).then((code) => ({ code, logs }));
}

test("알림: 열린 Issue 가 없으면 sync 라벨 Issue 를 만들고 1 (실행 빨간불)", async () => {
  const gh = fakeGh(null);
  const { code } = await run([status("2026-11-11T06:00:00", 2)], gh);
  assert.strictEqual(code, 1);
  assert.deepStrictEqual(gh.kinds(), ["issue list", "label create", "issue create"]);
  const create = gh.calls[2];
  assert.strictEqual(create[create.indexOf("--label") + 1], "sync");
  const body = create[create.indexOf("--body") + 1];
  assert.match(body, /2회 연속 실패/);
  assert.match(body, /2026-11-11T06:00:00/);
  assert.match(body, /actions\/runs\/9/);
});

test("알림 계속: 이미 열린 Issue 가 있으면 새로 안 만들고 0", async () => {
  const gh = fakeGh(42);
  const { code } = await run([status("2026-11-11T06:00:00", 3)], gh);
  assert.strictEqual(code, 0);
  assert.deepStrictEqual(gh.kinds(), ["issue list"]);
});

test("복구: 성공하면 열린 Issue 에 복구 시각을 남기고 닫음", async () => {
  const gh = fakeGh(42);
  const { code } = await run([status("2026-11-12T06:00:30", 0)], gh);
  assert.strictEqual(code, 0);
  assert.deepStrictEqual(gh.kinds(), ["issue list", "issue comment", "issue close"]);
  assert.strictEqual(gh.calls[2][2], "42");
  assert.match(gh.calls[1][4], /복구 확인: 2026-11-12 07:00 KST/);
});

test("정상 · 열린 Issue 없음: 아무것도 안 하고 0", async () => {
  const gh = fakeGh(null);
  assert.strictEqual((await run([status("2026-11-12T06:00:30", 0)], gh)).code, 0);
  assert.deepStrictEqual(gh.kinds(), ["issue list"]);
});

test("판단 보류(이력 없음 · 응답 없음): gh 를 아예 부르지 않음", async () => {
  const gh = fakeGh(42);
  assert.strictEqual((await run([status(null, 0)], gh)).code, 0);
  const down = await run([new Error("연결 실패"), 503, new Error("연결 실패")], gh);
  assert.strictEqual(down.code, 0);
  assert.strictEqual(down.logs.filter((l) => l.startsWith("시도")).length, 3); // 3번 다시 시도
  assert.deepStrictEqual(gh.calls, []);
});

test("다시 시도 중 응답이 오면 그 값으로 판단", async () => {
  const gh = fakeGh(null);
  const { code } = await run([503, status("2026-11-11T06:00:00", 2)], gh);
  assert.strictEqual(code, 1);
});

test("Issue 조회 실패 → 중복 생성을 막으려고 아무것도 안 하고 2", async () => {
  const gh = fakeGh(null, ["issue", "list"]);
  assert.strictEqual((await run([status("2026-11-11T06:00:00", 2)], gh)).code, 2);
  assert.deepStrictEqual(gh.kinds(), ["issue list"]);
});

test("닫기 실패 → 2", async () => {
  const gh = fakeGh(42, ["issue", "close"]);
  assert.strictEqual((await run([status("2026-11-12T06:00:30", 0)], gh)).code, 2);
});
