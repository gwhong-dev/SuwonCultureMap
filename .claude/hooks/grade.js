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

// gh 실행 부분: gh · gh.exe · 따옴표로 감싼 전체 경로('...\gh.exe') 모두
const GH = String.raw`\bgh(?:\.exe)?["']?`;
// 값을 받는 옵션 (값 안의 숫자를 PR 번호로 오해하지 않도록 값째 지움)
const VALUE_FLAGS = /(?:^|\s)(?:-t|--subject|-b|--body|-F|--body-file|-A|--author-email|--match-head-commit|-R|--repo)(?:=|\s+)\S+/g;

function repoOf(text) {
  return (text.match(/(?:^|\s)(?:-R|--repo)(?:=|\s+)["']?([\w.-]+\/[\w.-]+)/) || [])[1] || null;
}

// "gh pr merge 229 --merge" → { merge: true, pr: "229", repo: null, auto: false }
// "gh api -X PUT repos/o/r/pulls/229/merge" 도 머지로 봄
function parseMergeCommand(command) {
  const cmd = String(command || "");
  for (const part of cmd.split(/;|&&|\|\||\||\n/)) {
    const api = part.match(new RegExp(GH + String.raw`\s+api\b.*?repos\/([\w.-]+\/[\w.-]+)\/pulls\/(\d+)\/merge\b`));
    if (api) return { merge: true, pr: api[2], repo: api[1], auto: false };

    const m = part.match(new RegExp(GH + String.raw`\s+((?:(?:-R|--repo)(?:=|\s+)\S+\s+)?)pr\s+((?:(?:-R|--repo)(?:=|\s+)\S+\s+)?)merge\b(.*)$`));
    if (!m) continue;
    const raw = m[1] + " " + m[2] + " " + m[3];
    const args = m[3].replace(/"[^"]*"|'[^']*'/g, " Q ").replace(VALUE_FLAGS, " ");
    const pr =
      (m[3].match(/\/pull\/(\d+)/) || [])[1] ||
      (args.match(/(?:^|\s)#?(\d+)(?=\s|$)/) || [])[1] ||
      null;
    return { merge: true, pr, repo: repoOf(raw), auto: /(?:^|\s)--auto\b/.test(args) };
  }
  return { merge: false };
}

// gh 실행 파일: PATH 에 없으면(Git Bash 에서 흔함) 기본 설치 위치를 씀 → 훅이 매번 "못 읽음" 으로 빠지지 않게
function ghBinary() {
  const fs = require("node:fs");
  const path = require("node:path");
  const candidates = [];
  if (process.env.GH_PATH) candidates.push(process.env.GH_PATH);
  for (const base of [process.env.ProgramFiles, process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs")]) {
    if (base) candidates.push(path.join(base, "GitHub CLI", "gh.exe"));
  }
  return candidates.find((p) => fs.existsSync(p)) || "gh";
}

module.exports = { isLPath, lPaths, parseMergeCommand, ghBinary };
