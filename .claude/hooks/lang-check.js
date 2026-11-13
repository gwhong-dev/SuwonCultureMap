// 한국어 답변 지키기 (CLAUDE.md 6번 · 결정 기록 docs/decisions/2026-11-13-korean-reply-guard.md)
//  - Stop: 이번 턴 마지막 답변(last_assistant_message)이 영어면 멈춤을 막고 한국어로 다시 쓰게 함
//  - PreToolUse(mcp__ccd_turn__wrap_up): 다음 단계 상자의 요약(recap) · 질문(ask)이 영어면 거부
// 판정: 코드 · 경로 · 주소를 뺀 글에서 라틴 글자가 20개 이상이고, 한글 비율이 0.3 미만이면 "영어"
// 문제가 생기면(입력 깨짐 · 빈 글 · 이미 훅으로 이어 쓰는 중) 막지 않고 통과 (항상 exit 0)
// 턴 중간 진행 문구는 훅이 볼 수 없음 → settings 의 language 와 CLAUDE.md 로 줄임

const MIN_LATIN = 20;
const MAX_HANGUL_RATIO = 0.3;

const STOP_REASON =
  "마지막 답변이 영어였습니다 (CLAUDE.md 6번: 답변은 항상 한국어). " +
  "같은 내용을 한국어로만 다시 쓰세요. 도구를 다시 실행하지 말고, 사과나 앞 답변 언급 없이 답변 본문만 쓰세요.";
const WRAP_UP_REASON =
  "wrap_up 의 recap · ask · 버튼 글이 영어입니다 (CLAUDE.md 6번). 같은 내용을 한국어로 바꿔 wrap_up 을 다시 호출하세요.";

// 언어 판정에서 뺄 것: 코드 블록 · 인라인 코드 · 링크 주소 · URL · 경로나 파일 이름이 든 낱말
function stripNonProse(text) {
  return String(text)
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`[^`\n]*`/g, " ")
    .replace(/\]\([^)]*\)/g, "] ")
    .replace(/https?:\/\/\S+/g, " ")
    .split(/\s+/)
    .filter((w) => !/[\\/]/.test(w) && !/\.[A-Za-z0-9]{1,5}(?:[:)\],.]|$)/.test(w))
    .join(" ");
}

function isEnglish(text) {
  if (typeof text !== "string" || !text.trim()) return false;
  const prose = stripNonProse(text);
  const latin = (prose.match(/[A-Za-z]/g) || []).length;
  if (latin < MIN_LATIN) return false;
  const hangul = (prose.match(/[가-힣]/g) || []).length;
  return hangul / (hangul + latin) < MAX_HANGUL_RATIO;
}

// 훅 입력 → 출력할 JSON (통과면 null)
function decide(input) {
  if (!input || typeof input !== "object") return null;
  if (input.hook_event_name === "Stop") {
    if (input.stop_hook_active) return null; // 이미 이 훅으로 다시 쓰는 중 → 반복 방지
    return isEnglish(input.last_assistant_message) ? { decision: "block", reason: STOP_REASON } : null;
  }
  if (input.hook_event_name === "PreToolUse" && /wrap_up$/.test(String(input.tool_name || ""))) {
    const t = input.tool_input || {};
    const labels = Array.isArray(t.actions) ? t.actions.map((a) => (a && a.label) || "") : [];
    const text = [t.recap, t.ask, ...labels].filter((s) => typeof s === "string").join("\n");
    if (!isEnglish(text)) return null;
    return {
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason: WRAP_UP_REASON,
      },
    };
  }
  return null;
}

module.exports = { stripNonProse, isEnglish, decide, STOP_REASON, WRAP_UP_REASON };

if (require.main === module) {
  let raw = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (c) => (raw += c));
  process.stdin.on("end", () => {
    let out = null;
    try {
      out = decide(JSON.parse(raw));
    } catch {
      out = null; // 깨진 입력은 통과
    }
    if (out) process.stdout.write(JSON.stringify(out));
    process.exit(0);
  });
}
