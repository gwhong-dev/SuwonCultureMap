// PostToolUse 훅: 개발을 시작 · PR 생성 · 머지 · 분기 보관한 직후
// "통합문서 할 일 상태 옮기기"(CLAUDE.md 4-4)를 Claude 에게 한 줄로 상기시킨다.
//  해야할일 → (개발 시작) 진행 중 → (머지 + 배포 · 헬스 UP) 작업기록 > 날짜 탭
// 막지는 않음(additionalContext 만). 해당 없는 도구 · 명령이면 아무것도 출력하지 않음.
const { parseMergeCommand } = require("./grade.js");

const DOC = "통합문서(https://claude.ai/artifact/AjSEmZbxzk4UcCgDCR66Hh)";
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];

// 작업기록 날짜 탭 이름 "11/13 (금)" — 한국 시간 기준
function dayTab(now = new Date()) {
  const kst = new Date(now.getTime() + 9 * 3600 * 1000);
  const mm = String(kst.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(kst.getUTCDate()).padStart(2, "0");
  return `${mm}/${dd} (${WEEK[kst.getUTCDay()]})`;
}

// "git checkout -b feat/x" · "git switch -c feat/x" · "git worktree add ... -b feat/x" → "feat/x"
function newBranch(command) {
  for (const part of String(command || "").split(/;|&&|\|\||\||\n/)) {
    const m =
      part.match(/\bgit\s+checkout\b(?:\s+-[^\sbB]\S*)*\s+-[bB]\s+["']?([^\s"']+)/) ||
      part.match(/\bgit\s+switch\b(?:\s+-[^\scC]\S*)*\s+-[cC]\s+["']?([^\s"']+)/) ||
      part.match(/\bgit\s+worktree\s+add\b.*?\s-[bB]\s+["']?([^\s"']+)/);
    if (m) return m[1];
  }
  return null;
}

function isPrCreate(command) {
  return String(command || "")
    .split(/;|&&|\|\||\||\n/)
    .some((part) => /(?:^|[\s"'\/\\])gh(?:\.exe)?["']?\s+pr\s+create\b/.test(part) && !/\s--help\b/.test(part));
}

// 분기(다른 세션) 시작 도구인가 — 이름이 같은 Figma create_session(fileKey) 은 제외
function isSessionStart(toolName, toolInput) {
  const name = String(toolName || "");
  const input = toolInput || {};
  if (/(?:^|__)(start_session|hand_off_to_session)$/.test(name)) return true;
  if (/(?:^|__)create_session$/.test(name)) return !input.fileKey && !!(input.source_url || input.prompt);
  return false;
}

function message(input, now = new Date()) {
  if (!input || typeof input !== "object") return null;
  const tool = String(input.tool_name || "");
  const ti = input.tool_input || {};
  const tab = dayTab(now);

  if (tool === "Bash" || tool === "PowerShell") {
    const cmd = ti.command || "";
    const branch = newBranch(cmd);
    if (branch) {
      return `[통합문서 4-4 ①] 새 브랜치 \`${branch}\` 로 개발을 시작했으면, 이 답변 안에서 ${DOC} '해야할일' 의 해당 행(새 메모 · 원문 메모 포함)을 지우고 '진행 중' 표로 옮기세요. 해야할일에 없던 작업이면 '진행 중' 에 새 줄. 사용자에게 묻지 않음.`;
    }
    if (isPrCreate(cmd)) {
      return `[통합문서 4-4 ①] PR 을 만들었으면 통합문서 '진행 중' 행의 PR 칸을 #번호 로 채우세요.`;
    }
    const merge = parseMergeCommand(cmd);
    if (merge.merge && !merge.auto) {
      return `[통합문서 4-4 ②] 머지했으면 Deploy 성공 · 헬스 UP 을 확인한 그 답변 안에서 통합문서 '진행 중' 의 ${merge.pr ? "#" + merge.pr + " " : ""}행을 지우고 '작업기록 > ${tab}' 탭 표 맨 아래로 옮기세요 (탭이 없으면 만듦).`;
    }
    return null;
  }

  if (isSessionStart(tool, ti)) {
    return `[통합문서 4-4 ①] 분기 세션이 시작됐으면, 이 답변 안에서 통합문서 '해야할일' 의 해당 행을 '진행 중' 표로 옮기고 맡은 곳에 분기 이름을 적으세요 (분기는 통합문서를 못 열 수 있어 메인이 함). 시작되지 않았으면 그대로 둠.`;
  }

  if (/(?:^|__)archive_session$/.test(tool)) {
    return `[통합문서 4-4 ②] 보관한 분기가 [분기 완료] 였다면 통합문서 '진행 중' 행을 '작업기록 > ${tab}' 으로 옮겼는지 확인하세요.`;
  }
  return null;
}

module.exports = { dayTab, newBranch, isPrCreate, isSessionStart, message };

if (require.main === module) {
  let input = {};
  try {
    input = JSON.parse(require("node:fs").readFileSync(0, "utf8") || "{}");
  } catch {
    process.exit(0);
  }
  const text = message(input);
  if (text) {
    process.stdout.write(
      JSON.stringify({ hookSpecificOutput: { hookEventName: "PostToolUse", additionalContext: text } })
    );
  }
  process.exit(0);
}
