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

test("영어 로그 · PR 목록 · 커밋 목록 · 영어 항목 표가 섞인 한국어 보고는 통과 (검증 지적 오탐)", () => {
  for (const msg of [
    "테스트 결과입니다.\nTests run: 731, Failures: 0, Errors: 0, Skipped: 0\nBUILD SUCCESS\nTotal time: 01:23 min",
    "열린 PR:\n- #246 chore: add Korean reply guard hook and language setting\n- #247 docs: clarify Korean reply rule in CLAUDE.md\n둘 다 CI 통과.",
    "커밋 3개:\nfeat: add NonTouristFilter for instagram posts\nfix: keep spacing when matching keywords\ndocs: update decision record",
    "| 항목 | 결과 |\n|---|---|\n| Stop hook output shape | ✅ |\n| PreToolUse deny output | ✅ |\n| 설정 파일 연결 | ✅ |",
    "CI 결과: `build-and-test` `docker-image` `Analyze (java)` `CodeQL` 모두 성공",
  ]) {
    assert.strictEqual(isEnglish(msg), false, msg);
  }
});

test("한국어 인용이 조금 섞인 영어 답변은 여전히 막음", () => {
  assert.strictEqual(isEnglish('The review verdict is "보완 후 통과" (pass after fixes), with 4 items. I\'ll fix all four in this PR.'), true);
  assert.strictEqual(isEnglish("While the full suite runs, I'm updating the \"조치\" (action) column of the same review comment."), true);
});

test("wrap_up: 요약이 한국어여도 버튼이 영어면 거부", () => {
  const out = decide(wrapUp({ recap: "검증 반영 완료. CI 진행 중입니다.", actions: [{ label: "Yes, merge" }, { label: "Not now" }] }));
  assert.strictEqual(out.hookSpecificOutput.permissionDecision, "deny");
  assert.strictEqual(decide(wrapUp({ recap: "검증 반영 완료.", actions: [{ label: "PR 머지" }, { action: "commit" }] })), null);
});

test("설정 연결: language korean · Stop 훅 · wrap_up PreToolUse 훅이 lang-check.js 를 부름", () => {
  const settings = JSON.parse(require("node:fs").readFileSync(path.join(__dirname, "../../../.claude/settings.json"), "utf8"));
  assert.strictEqual(settings.language, "korean");
  const cmds = (list) => (list || []).flatMap((h) => h.hooks.map((x) => x.command));
  assert.ok(cmds(settings.hooks.Stop).some((c) => c.includes("lang-check.js")));
  const wrap = settings.hooks.PreToolUse.find((h) => h.matcher === "mcp__ccd_turn__wrap_up");
  assert.ok(wrap && cmds([wrap]).some((c) => c.includes("lang-check.js")));
  assert.ok(cmds(settings.hooks.PreToolUse).some((c) => c.includes("guard-l-merge.js"))); // 기존 훅 유지
});
