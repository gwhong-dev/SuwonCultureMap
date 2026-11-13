// 대화 길이 지키기 훅 시험 (.claude/hooks/session-length.js)
const test = require("node:test");
const assert = require("node:assert");
const { execFileSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { WARN, BLOCK, measure, decide } = require("../../../.claude/hooks/session-length.js");

const HOOK = path.join(__dirname, "../../../.claude/hooks/session-length.js");

// 대화 기록 한 줄 만들기
const reply = (cacheRead, extra = {}) =>
  JSON.stringify({
    type: "assistant",
    message: { usage: { input_tokens: 2, cache_creation_input_tokens: 1000, cache_read_input_tokens: cacheRead }, content: [{ type: "text", text: "답변" }] },
    ...extra,
  });
const toolCall = (name) => JSON.stringify({ type: "assistant", message: { content: [{ type: "tool_use", name, input: {} }] } });
const user = JSON.stringify({ type: "user", message: { content: "질문" } });
const log = (...lines) => lines.join("\n") + "\n";

const prompt = { hook_event_name: "UserPromptSubmit" };
const stop = (extra = {}) => ({ hook_event_name: "Stop", ...extra });

test("대화 크기 = 마지막 응답의 input + cache_creation + cache_read", () => {
  assert.strictEqual(measure(log(reply(50000), user, reply(120000))).context, 121002);
});

test("서브 에이전트 기록 · 깨진 줄 · 사용량 없는 줄은 무시", () => {
  const text = log(reply(150000), "{깨진", reply(900000, { isSidechain: true }), toolCall("Bash"), user);
  assert.strictEqual(measure(text).context, 151002);
});

test("경고 기준 아래면 아무것도 안 함", () => {
  const text = log(reply(WARN - 5000));
  assert.strictEqual(decide(prompt, text), null);
  assert.strictEqual(decide(stop(), text), null);
});

test("경고 기준부터 질문마다 넘기기 안내를 보여 줌 (막지는 않음)", () => {
  const text = log(reply(WARN));
  const out = decide(prompt, text);
  assert.strictEqual(out.hookSpecificOutput.hookEventName, "UserPromptSubmit");
  assert.match(out.hookSpecificOutput.additionalContext, /작업 단계.*끝나면 새 세션/);
  assert.strictEqual(decide(stop(), text), null);
});

test("차단 기준을 넘으면 새 세션 카드 전까지 턴을 못 끝냄", () => {
  const out = decide(stop(), log(reply(BLOCK)));
  assert.strictEqual(out.decision, "block");
  assert.match(out.reason, /start_session/);
  assert.match(out.reason, /\[분기 막힘\] 대화 길이 초과/);
});

test("차단 기준을 넘은 뒤 start_session · hand_off_to_session 을 부르면 통과", () => {
  assert.strictEqual(decide(stop(), log(reply(BLOCK), toolCall("mcp__ccd_session__start_session"), reply(BLOCK + 3000))), null);
  assert.strictEqual(decide(stop(), log(reply(BLOCK), toolCall("mcp__ccd_session__hand_off_to_session"))), null);
});

test("차단 기준을 넘기 전에 만든 카드(분기 시작 등)는 인정하지 않음", () => {
  const out = decide(stop(), log(toolCall("mcp__ccd_session__start_session"), reply(BLOCK + 3000)));
  assert.strictEqual(out.decision, "block");
});

test("비슷한 이름의 다른 도구는 카드로 치지 않음", () => {
  const out = decide(stop(), log(reply(BLOCK), toolCall("mcp__ccd_session__dismiss_task"), toolCall("restart_session_x")));
  assert.strictEqual(out.decision, "block");
});

test("이미 훅으로 이어 쓰는 중(stop_hook_active)이면 통과 → 같은 턴에 무한 반복 없음", () => {
  assert.strictEqual(decide(stop({ stop_hook_active: true }), log(reply(BLOCK * 2))), null);
});

test("다른 이벤트 · 이상한 입력은 통과", () => {
  assert.strictEqual(decide({ hook_event_name: "PreToolUse" }, log(reply(BLOCK))), null);
  assert.strictEqual(decide(null, ""), null);
  assert.strictEqual(decide(stop(), undefined), null);
  assert.strictEqual(decide(stop(), ""), null);
});

test("실제 실행: 기록 파일을 읽어 차단 JSON 출력, 파일이 없거나 입력이 깨지면 조용히 통과", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "session-length-"));
  const file = path.join(dir, "t.jsonl");
  fs.writeFileSync(file, log(user, reply(BLOCK + 50000)));
  const run = (input) => execFileSync("node", [HOOK], { input, encoding: "utf8" });
  assert.strictEqual(JSON.parse(run(JSON.stringify({ hook_event_name: "Stop", transcript_path: file }))).decision, "block");
  assert.match(JSON.parse(run(JSON.stringify({ hook_event_name: "UserPromptSubmit", transcript_path: file }))).hookSpecificOutput.additionalContext, /35만/);
  assert.strictEqual(run(JSON.stringify({ hook_event_name: "Stop", transcript_path: path.join(dir, "없음.jsonl") })), "");
  assert.strictEqual(run("{깨진"), "");
  fs.rmSync(dir, { recursive: true, force: true });
});
