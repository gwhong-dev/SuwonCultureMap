// 한국어 답변 측정 (결정 기록 docs/decisions/2026-11-13-korean-reply-guard.md)
// 사용: node scripts/lang-audit.js <대화 기록 .jsonl ...>
//   대화 기록 위치: 사용자 폴더의 .claude/projects/<프로젝트 폴더 이름>/*.jsonl (PC 마다 다름)
// 판정은 훅과 같은 함수(.claude/hooks/lang-check.js isEnglish). 글 내용은 출력하지 않고 개수만 셈
const fs = require("node:fs");
const path = require("node:path");
const { isEnglish } = require("../.claude/hooks/lang-check.js");

function entries(file) {
  return fs.readFileSync(file, "utf8").split("\n").flatMap((l) => {
    try {
      return [JSON.parse(l)];
    } catch {
      return [];
    }
  });
}

// 사람이 쓴 입력 (도구 결과가 아닌 user 줄)
function isHumanTurn(e) {
  if (e.type !== "user" || !e.message) return false;
  const c = e.message.content;
  return typeof c === "string" || (Array.isArray(c) && c.some((b) => b.type === "text"));
}

const total = { texts: 0, english: 0, finals: 0, finalEnglish: 0, bash: 0, bashEnglish: 0 };
for (const file of process.argv.slice(2)) {
  const t = { texts: 0, english: 0, finals: 0, finalEnglish: 0, bash: 0, bashEnglish: 0 };
  let lastText = null;
  const closeTurn = () => {
    if (lastText === null) return;
    t.finals++;
    if (isEnglish(lastText)) t.finalEnglish++;
    lastText = null;
  };
  for (const e of entries(file)) {
    if (isHumanTurn(e)) closeTurn();
    if (e.type !== "assistant" || !e.message || !Array.isArray(e.message.content)) continue;
    for (const b of e.message.content) {
      if (b.type === "text" && b.text.trim()) {
        t.texts++;
        if (isEnglish(b.text)) t.english++;
        lastText = b.text;
      } else if (b.type === "tool_use" && /^(Bash|PowerShell)$/.test(b.name) && b.input && b.input.description) {
        t.bash++;
        // 설명은 짧아서 한글이 한 글자도 없으면 영어로 봄
        if (!/[가-힣]/.test(b.input.description)) t.bashEnglish++;
      }
    }
  }
  closeTurn();
  for (const k of Object.keys(total)) total[k] += t[k];
  console.log(`${path.basename(file).slice(0, 8)}  글 영어 ${t.english}/${t.texts} · 턴 마지막 영어 ${t.finalEnglish}/${t.finals} · Bash 설명 영어 ${t.bashEnglish}/${t.bash}`);
}
const pct = (a, b) => (b ? Math.round((a / b) * 100) : 0) + "%";
console.log(`\n합계  글 영어 ${pct(total.english, total.texts)} (${total.english}/${total.texts}) · 턴 마지막 영어 ${pct(total.finalEnglish, total.finals)} (${total.finalEnglish}/${total.finals}) · Bash 설명 영어 ${pct(total.bashEnglish, total.bash)}`);
