// SessionStart 훅: 세션을 열면 git 상태를 한 번 보고 이어가기 전에 알릴 것을 모음 (CLAUDE.md 2번 "세션 시작")
//  - 커밋 안 된 변경 · push 안 된 커밋(모든 로컬 브랜치) · main 이 origin 보다 뒤처짐 · 열린 PR
// 실패해도 세션을 막지 않음 (항상 exit 0)
const { execFileSync } = require("node:child_process");

function run(cmd, args, timeout = 15000) {
  try {
    return execFileSync(cmd, args, {
      encoding: "utf8",
      timeout,
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return null;
  }
}

const notes = [];

const fetched = run("git", ["fetch", "--prune", "--quiet"], 30000) !== null;
if (!fetched) notes.push("⚠️ git fetch 실패 (오프라인?) — 아래 비교는 마지막 fetch 기준");

const dirty = run("git", ["status", "--porcelain"]);
if (dirty) notes.push(`⚠️ 커밋 안 된 변경 ${dirty.split("\n").length}개 파일`);

// 브랜치마다 upstream 대비 ahead/behind, upstream 없는 브랜치는 push 안 된 것
const refs = run("git", ["for-each-ref", "--format=%(refname:short)\t%(upstream:short)\t%(upstream:track)", "refs/heads"]);
if (refs) {
  for (const line of refs.split("\n")) {
    const [branch, upstream, track] = line.split("\t");
    if (!upstream) {
      const only = run("git", ["rev-list", "--count", branch, "--not", "--remotes"]);
      if (only && only !== "0") notes.push(`⚠️ ${branch}: 원격에 없는 커밋 ${only}개 (push 안 됨)`);
      continue;
    }
    const ahead = /ahead (\d+)/.exec(track || "");
    const behind = /behind (\d+)/.exec(track || "");
    if (ahead) notes.push(`⚠️ ${branch}: push 안 된 커밋 ${ahead[1]}개`);
    if (behind && branch === "main") notes.push(`ℹ️ main: origin 보다 ${behind[1]}개 뒤 → 작업 트리가 깨끗하면 pull`);
  }
}

const prs = run(require("./grade.js").ghBinary(), ["pr", "list", "--state", "open", "--json", "number,title,headRefName,isDraft", "-q", '.[] | "#\\(.number) \\(.headRefName)\\(if .isDraft then " (Draft)" else "" end) — \\(.title)"']);
if (prs === null) notes.push("ℹ️ gh 로 열린 PR 을 못 읽음 (gh 설치 · 로그인 확인)");
else if (prs) notes.push("ℹ️ 열린 PR (다른 PC 에서 하던 것일 수 있음):\n" + prs.split("\n").map((l) => "  " + l).join("\n"));

const summary = notes.length ? notes.join("\n") : "✅ git 깨끗 — 커밋 안 된 변경 · push 안 된 커밋 · 열린 PR 없음";
process.stdout.write(
  JSON.stringify({
    systemMessage: "[세션 시작 점검]\n" + summary,
    hookSpecificOutput: {
      hookEventName: "SessionStart",
      additionalContext: "[세션 시작 점검 — CLAUDE.md 2번, 사용자에게 먼저 알릴 것]\n" + summary,
    },
  })
);
process.exit(0);
