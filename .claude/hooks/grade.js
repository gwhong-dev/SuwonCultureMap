// 등급표(CLAUDE.md 4번)의 L 경로를 기계적으로 판정 — 머지 차단 훅과 시험이 같이 씀
// 로그인 · 결제 · 개인정보 처리처럼 경로로 못 정하는 L 은 여기서 잡지 않음 (사람 판단)
const L_PATTERNS = [
  /^CLAUDE\.md$/,
  /^\.claude\//,
  /^\.github\//,
  /^src\/main\/java\/com\/suwon\/festival\/(security|config|entity)\//,
  /^src\/main\/resources\/db\/migration\//,
  /^src\/main\/resources\/application[^/]*\.properties$/,
  /^Dockerfile$/,
  /^docker-compose[^/]*$/,
  /^deploy\//,
  /^Jenkinsfile$/,
];

function isLPath(path) {
  const p = String(path).replace(/\\/g, "/").replace(/^\.\//, "");
  return L_PATTERNS.some((re) => re.test(p));
}

function lPaths(paths) {
  return paths.filter(isLPath);
}

// "gh pr merge 229 --merge" → { merge: true, pr: "229", auto: false }
function parseMergeCommand(command) {
  const cmd = String(command || "");
  const m = cmd.match(/\bgh(?:\.exe)?\s+pr\s+merge\b([^;&|\n]*)/);
  if (!m) return { merge: false };
  const args = m[1];
  const pr = (args.match(/(?:^|\s)#?(\d+)(?=\s|$)/) || args.match(/\/pull\/(\d+)/) || [])[1] || null;
  return { merge: true, pr, auto: /(?:^|\s)--auto\b/.test(args) };
}

module.exports = { isLPath, lPaths, parseMergeCommand };
