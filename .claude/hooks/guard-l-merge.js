// PreToolUse 훅: `gh pr merge` 를 실행하기 전에 PR 파일을 보고
//  - --auto (auto-merge) → 막음 (CLAUDE.md 4번 7: auto-merge 안 씀)
//  - L 경로가 있으면 → 사용자에게 허용을 물음 (L = 사용자 "머지" 승인 뒤에만)
//  - 파일 목록을 못 읽으면 → 안전하게 물음
// 그 밖(S · M)은 아무것도 출력하지 않아 평소 권한 규칙대로 진행
const { execFileSync } = require("node:child_process");
const { lPaths, parseMergeCommand, ghBinary } = require("./grade.js");

function readStdin() {
  try {
    return require("node:fs").readFileSync(0, "utf8");
  } catch {
    return "";
  }
}

function decide(decision, reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: decision,
        permissionDecisionReason: reason,
      },
    })
  );
  process.exit(0);
}

let input = {};
try {
  input = JSON.parse(readStdin() || "{}");
} catch {
  process.exit(0);
}
const command = (input.tool_input && input.tool_input.command) || "";
const parsed = parseMergeCommand(command);
if (!parsed.merge) process.exit(0);

if (parsed.auto) {
  decide("deny", "auto-merge(--auto) 는 쓰지 않음 (CLAUDE.md 4번 7). CI 통과 뒤 직접 머지하세요.");
}

let files;
try {
  const args = ["pr", "view"];
  if (parsed.pr) args.push(parsed.pr);
  if (parsed.repo) args.push("-R", parsed.repo);
  args.push("--json", "files", "-q", ".files[].path");
  const out = execFileSync(ghBinary(), args, {
    cwd: input.cwd || process.cwd(),
    encoding: "utf8",
    timeout: 20000,
    stdio: ["ignore", "pipe", "ignore"],
  });
  files = out.split(/\r?\n/).filter(Boolean);
} catch {
  decide("ask", "PR 파일 목록을 못 읽어 등급을 확인하지 못함 → L 일 수 있으니 사용자 '머지' 승인 뒤에만 허용하세요.");
}

const hits = lPaths(files);
if (hits.length > 0) {
  const shown = hits.slice(0, 5).join(", ") + (hits.length > 5 ? ` 외 ${hits.length - 5}개` : "");
  decide("ask", `L 등급 PR (${shown}) — 사용자가 채팅에서 "머지" 라고 했을 때만 허용하세요.`);
}
process.exit(0);
