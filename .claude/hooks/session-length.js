// 대화 길이 지키기 (CLAUDE.md 8번 "메인 교체" · 결정 기록 docs/decisions/2026-11-13-session-length-hook.md)
//  - 대화 크기 = 대화 기록(transcript)의 마지막 응답 토큰 사용량 (input + cache_creation + cache_read)
//  - UserPromptSubmit: WARN 이상이면 "지금 작업 단계가 끝나면 넘기기" 를 Claude 에게 보여 줌
//  - Stop: BLOCK 이상인데 그 뒤로 새 세션 카드(start_session · hand_off_to_session)를 안 만들었으면 멈춤을 막음
// 기준: 새 세션 시작 비용 ≈ 기본 지침 · 도구 목록 10만 토큰 중 4만 새로 읽기 (11/13 실측) → 20만부터 경고, 30만부터 차단
// 문제가 생기면(입력 깨짐 · 기록 파일 없음 · 이미 훅으로 이어 쓰는 중) 막지 않고 통과 (항상 exit 0)

const fs = require("node:fs");

const WARN = 200000;
const BLOCK = 300000;
const HANDOFF_TOOL = /(?:^|__)(?:start_session|hand_off_to_session)$/;

const man = (n) => `${Math.round(n / 10000)}만`;

const warnText = (n) =>
  `[대화 길이 ${man(n)} 토큰 — CLAUDE.md 8번] 경고 기준 ${man(WARN)} 을 넘었습니다. ` +
  `이번 요청의 작업 단계(PR 머지 · 커밋 · push)가 끝나면 새 세션으로 넘기세요. ` +
  `기능 개발 중간이면 끝까지 마친 뒤 넘깁니다 (중간에 넘기면 코드를 다시 읽어 손해). ` +
  `${man(BLOCK)} 을 넘으면 새 세션 카드를 만들기 전까지 턴을 끝낼 수 없습니다.`;

const blockText = (n) =>
  `[대화 길이 ${man(n)} 토큰 — CLAUDE.md 8번] 차단 기준 ${man(BLOCK)} 을 넘었습니다. 턴을 끝내기 전에 새 세션으로 넘기세요: ` +
  `① 진행 중인 작업은 커밋 · push (덜 끝났으면 wip: 커밋 + Draft PR) ` +
  `② git fetch 뒤 origin/main 의 PROGRESS.md 상단 + 열린 PR 로 인수인계(8번 형식) ` +
  `③ start_session(initiation "offer") 로 새 세션 카드. ` +
  `분기 세션이면 새 세션을 만들지 말고 마지막 답변 첫 줄을 "[분기 막힘] 대화 길이 초과" 로 쓰세요.`;

// 대화 기록 한 줄(JSON) → 응답이면 { context } , 새 세션 도구 호출이 있으면 handoff: true
function readLine(line) {
  let e;
  try {
    e = JSON.parse(line);
  } catch {
    return null;
  }
  if (!e || e.type !== "assistant" || e.isSidechain) return null; // 서브 에이전트 기록은 뺌
  const m = e.message || {};
  const u = m.usage;
  const context = u ? (u.input_tokens || 0) + (u.cache_creation_input_tokens || 0) + (u.cache_read_input_tokens || 0) : 0;
  const handoff = Array.isArray(m.content) && m.content.some((c) => c && c.type === "tool_use" && HANDOFF_TOOL.test(String(c.name || "")));
  return { context, handoff };
}

// 대화 기록 글 → { context: 마지막 대화 크기, handoffAfterBlock: 차단 기준을 처음 넘은 뒤 새 세션 카드를 만들었나 }
function measure(text) {
  let context = 0;
  let crossed = false;
  let handoffAfterBlock = false;
  for (const line of String(text).split("\n")) {
    if (!line.trim()) continue;
    const r = readLine(line);
    if (!r) continue;
    if (r.context) context = r.context;
    if (context >= BLOCK) crossed = true;
    if (crossed && r.handoff) handoffAfterBlock = true;
  }
  return { context, handoffAfterBlock };
}

// 훅 입력 + 대화 기록 글 → 출력할 JSON (통과면 null)
function decide(input, transcript) {
  if (!input || typeof input !== "object" || typeof transcript !== "string") return null;
  const { context, handoffAfterBlock } = measure(transcript);
  if (input.hook_event_name === "UserPromptSubmit") {
    if (context < WARN) return null;
    return { hookSpecificOutput: { hookEventName: "UserPromptSubmit", additionalContext: warnText(context) } };
  }
  if (input.hook_event_name === "Stop") {
    if (input.stop_hook_active) return null; // 이미 이 턴에 한 번 막음 → 반복 방지 (다음 턴에 다시 막음)
    if (context < BLOCK || handoffAfterBlock) return null;
    return { decision: "block", reason: blockText(context) };
  }
  return null;
}

module.exports = { WARN, BLOCK, measure, decide, warnText, blockText };

if (require.main === module) {
  let raw = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (c) => (raw += c));
  process.stdin.on("end", () => {
    let out = null;
    try {
      const input = JSON.parse(raw);
      out = decide(input, fs.readFileSync(input.transcript_path, "utf8"));
    } catch {
      out = null; // 깨진 입력 · 기록 파일 없음은 통과
    }
    if (out) process.stdout.write(JSON.stringify(out));
    process.exit(0);
  });
}
