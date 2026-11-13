// 한국어 답변 지키기 훅 시험 (.claude/hooks/lang-check.js)
const test = require("node:test");
const assert = require("node:assert");
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const { isEnglish, decide, stripNonProse } = require("../../../.claude/hooks/lang-check.js");

const HOOK = path.join(__dirname, "../../../.claude/hooks/lang-check.js");
const stop = (msg, extra = {}) => ({ hook_event_name: "Stop", last_assistant_message: msg, ...extra });
const wrapUp = (input) => ({ hook_event_name: "PreToolUse", tool_name: "mcp__ccd_turn__wrap_up", tool_input: input });

test("한국어 답변은 통과 (영어 용어 · PR 제목이 섞여도)", () => {
  assert.strictEqual(isEnglish("PR #245 머지 완료, 배포 성공 · 운영 헬스 UP 입니다. CI 4개 모두 통과했습니다."), false);
  assert.strictEqual(isEnglish("`NonTouristFilter` 를 [CaptionParser.java](src/main/java/x/CaptionParser.java) 에 연결했어요. Stop hook 의 last_assistant_message 로 판정합니다."), false);
});

test("영어 답변은 막음", () => {
  assert.strictEqual(isEnglish("I've opened PR #245, which keeps non-tourist Instagram posts off the map. All tests pass."), true);
  assert.deepStrictEqual(decide(stop("Tests written; fixing one test sentence, then running the full suite now.")).decision, "block");
});

test("코드 · 경로 · 주소 위주이거나 짧은 글은 통과", () => {
  assert.strictEqual(isEnglish("```bash\n./mvnw.cmd test -Dtest=NonTouristFilterTest\n```"), false);
  assert.strictEqual(isEnglish("https://github.com/gwhong-dev/SuwonCultureMap/pull/245"), false);
  assert.strictEqual(isEnglish("PR #245 ✅ merged"), false); // 라틴 20자 미만
  assert.strictEqual(isEnglish("src/main/java/com/suwon/festival/service/caption/NonTouristFilter.java"), false);
  assert.ok(!stripNonProse("see C:\\dev\\x.js and docs/a.md").includes("docs"));
});

test("빈 글 · 깨진 입력 · 이미 다시 쓰는 중이면 통과 (무한 반복 방지)", () => {
  assert.strictEqual(decide(stop("")), null);
  assert.strictEqual(decide(stop(undefined)), null);
  assert.strictEqual(decide(null), null);
  assert.strictEqual(decide(stop("This answer is entirely in English and quite long.", { stop_hook_active: true })), null);
});

test("wrap_up: 요약 · 질문 · 버튼이 영어면 거부, 한국어면 통과, 다른 도구는 무시", () => {
  const en = decide(wrapUp({ recap: "All 4 review findings are fixed and pushed to PR #245.", ask: "Merge once CI passes?" }));
  assert.strictEqual(en.hookSpecificOutput.permissionDecision, "deny");
  assert.strictEqual(decide(wrapUp({ recap: "검증 지적 4개를 모두 반영해 PR #245 에 push 했습니다. CI 진행 중.", actions: [{ label: "CI 확인 후 머지" }] })), null);
  assert.strictEqual(decide({ hook_event_name: "PreToolUse", tool_name: "Bash", tool_input: { command: "echo hello world from english" } }), null);
});

test("실제 실행: Stop 에 영어면 block JSON, 한국어 · 깨진 입력은 아무것도 안 씀 (exit 0)", () => {
  const run = (stdin) => execFileSync("node", [HOOK], { input: stdin, encoding: "utf8" });
  const out = JSON.parse(run(JSON.stringify(stop("I fixed the merge conflict on PR #245 and pushed it to the branch."))));
  assert.strictEqual(out.decision, "block");
  assert.match(out.reason, /한국어로만 다시/);
  assert.strictEqual(run(JSON.stringify(stop("머지 충돌을 고쳐 push 했습니다."))), "");
  assert.strictEqual(run("{not json"), "");
});
