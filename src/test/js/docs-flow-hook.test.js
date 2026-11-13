// 통합문서 할 일 상태 옮기기 알림 훅 시험 (.claude/hooks/docs-flow.js, CLAUDE.md 4-4)
const test = require("node:test");
const assert = require("node:assert");
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const { dayTab, newBranch, isPrCreate, isSessionStart, message } = require("../../../.claude/hooks/docs-flow.js");

const HOOK = path.join(__dirname, "../../../.claude/hooks/docs-flow.js");
const bash = (command) => ({ hook_event_name: "PostToolUse", tool_name: "Bash", tool_input: { command } });
const NOW = new Date("2026-11-13T05:00:00Z"); // 한국 14시, 금요일

test("작업기록 날짜 탭 이름은 한국 시간 'MM/DD (요일)'", () => {
  assert.strictEqual(dayTab(NOW), "11/13 (금)");
  assert.strictEqual(dayTab(new Date("2026-11-13T16:00:00Z")), "11/14 (토)"); // 한국은 이미 다음 날
});

test("개발 시작(새 브랜치) → '진행 중' 으로 옮기라는 알림", () => {
  assert.strictEqual(newBranch("git fetch && git checkout -b feat/landmark-page origin/main"), "feat/landmark-page");
  assert.strictEqual(newBranch("git switch -c fix/toast"), "fix/toast");
  assert.strictEqual(newBranch("git checkout -q -b chore/docs-todo-flow origin/main"), "chore/docs-todo-flow");
  assert.strictEqual(newBranch("git worktree add ../wt -b feat/x origin/main"), "feat/x");
  assert.strictEqual(newBranch("git checkout main && git pull"), null);
  const text = message(bash("git checkout -b feat/landmark-page origin/main"), NOW);
  assert.match(text, /4-4 ①/);
  assert.match(text, /진행 중/);
  assert.match(text, /feat\/landmark-page/);
});

test("PR 생성 → PR 칸 채우기 알림", () => {
  assert.ok(isPrCreate('gh pr create --title "feat: x" --body-file b.md'));
  assert.ok(isPrCreate('"/c/Program Files/GitHub CLI/gh.exe" pr create --fill'));
  assert.ok(!isPrCreate("gh pr create --help"));
  assert.ok(!isPrCreate("gh pr list"));
  assert.match(message(bash("gh pr create --fill"), NOW), /PR 칸/);
});

test("머지 → 배포 · 헬스 뒤 작업기록 날짜 탭으로 옮기기 알림 (--auto 는 제외)", () => {
  const text = message(bash("gh pr merge 258 --merge"), NOW);
  assert.match(text, /4-4 ②/);
  assert.match(text, /#258/);
  assert.match(text, /작업기록 > 11\/13 \(금\)/);
  assert.strictEqual(message(bash("gh pr merge 258 --auto --merge"), NOW), null);
});

test("분기 시작 · 보관 도구 → 알림, Figma create_session 은 무시", () => {
  assert.ok(isSessionStart("mcp__ccd_session__start_session", { title: "명소 ②" }));
  assert.ok(isSessionStart("mcp__x__create_session", { source_url: "https://github.com/o/r", prompt: "작업 지시서" }));
  assert.ok(!isSessionStart("mcp__figma__create_session", { fileKey: "abc", task: "x" }));
  assert.match(message({ tool_name: "mcp__ccd_session__start_session", tool_input: {} }, NOW), /분기 이름/);
  assert.match(message({ tool_name: "mcp__ccd_session_mgmt__archive_session", tool_input: {} }, NOW), /분기 완료/);
});

test("관계없는 명령 · 깨진 입력은 아무것도 안 함", () => {
  assert.strictEqual(message(bash("./mvnw.cmd test"), NOW), null);
  assert.strictEqual(message(bash("git status"), NOW), null);
  assert.strictEqual(message({ tool_name: "Read", tool_input: { file_path: "x" } }, NOW), null);
  assert.strictEqual(message(null, NOW), null);
});

test("실행: stdin JSON → PostToolUse additionalContext 출력, 해당 없으면 빈 출력", () => {
  const out = execFileSync("node", [HOOK], { input: JSON.stringify(bash("git checkout -b feat/y")), encoding: "utf8" });
  const parsed = JSON.parse(out);
  assert.strictEqual(parsed.hookSpecificOutput.hookEventName, "PostToolUse");
  assert.match(parsed.hookSpecificOutput.additionalContext, /feat\/y/);
  assert.strictEqual(execFileSync("node", [HOOK], { input: JSON.stringify(bash("ls")), encoding: "utf8" }), "");
  assert.strictEqual(execFileSync("node", [HOOK], { input: "깨진 JSON", encoding: "utf8" }), "");
});
