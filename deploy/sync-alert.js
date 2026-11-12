#!/usr/bin/env node
// =====================================================
//  재단 동기화 실패 알림 — .github/workflows/sync-alert.yml 이 1시간마다 실행
//  1) 운영 GET /api/sync-status 읽기 (lastSuccessAt = 재단 마지막 성공(한국 시간), consecutiveFailures = 그 뒤 연속 실패)
//  2) 판단 (decide):
//     - alert  : 연속 실패 2회 이상 또는 마지막 성공이 13시간 넘게 지남 (동기화 06 · 18시 → 한 번 빠지면 13시간)
//     - ok     : 마지막 성공이 13시간 안 + 연속 실패 2회 미만
//     - unknown: 응답 없음 · 형식 이상 · 성공 이력 없음(막 배포 · 첫 동기화 전) → Issue 를 건드리지 않음
//       (응답 없음은 가동 감시(uptime.yml)가 따로 Issue 를 엶)
//  3) alert → "sync" 라벨 Issue 1개만 (이미 열려 있으면 새로 안 만듦) / ok → 열린 Issue 에 복구 시각 남기고 닫음
//  종료 코드: 0 정상 · 복구 · 알림 계속 · 판단 보류 / 1 새 알림 / 2 gh 조회 · 닫기 실패(Issue 건드리지 않음)
//  → uptime-check.sh 와 같은 규칙. 오류 내용은 API 에 없음(횟수 · 시각만) → Issue 에도 안 들어감
//  시험: node --test src/test/js/sync-alert.test.js (가짜 fetch · gh)
// =====================================================
"use strict";

const LABEL = "sync";
const DEFAULTS = {
  url: "https://suwonbible.com/api/sync-status",
  failLimit: 2,
  staleHours: 13,
  tries: 3,
  waitMs: 10000,
};

// 서버가 주는 "2026-11-11T06:00:12" 는 한국 시간 값 → +09:00 을 붙여 시각(ms)으로
function parseKst(text) {
  if (typeof text !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(text)) return NaN;
  return Date.parse(text + "+09:00");
}

function formatKst(ms) {
  const d = new Date(ms + 9 * 3600 * 1000); // UTC 값으로 읽으면 한국 시간
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())} KST`;
}

// status: /api/sync-status 응답(객체) 또는 null(못 받음). → { state, reasons }
function decide(status, nowMs, opts) {
  const o = Object.assign({}, DEFAULTS, opts);
  if (!status || typeof status !== "object" || Array.isArray(status)) {
    return { state: "unknown", reasons: ["sync-status 응답을 받지 못함 (가동 감시가 따로 확인)"] };
  }
  const cf = status.consecutiveFailures;
  const cfKnown = Number.isInteger(cf) && cf >= 0;
  const last = status.lastSuccessAt;
  const lastMs = last == null ? null : parseKst(last);
  if (Number.isNaN(lastMs)) {
    return { state: "unknown", reasons: ["lastSuccessAt 형식 이상"] };
  }

  const reasons = [];
  if (cfKnown && cf >= o.failLimit) reasons.push(`재단 동기화 ${cf}회 연속 실패`);
  if (lastMs != null) {
    const ageHours = (nowMs - lastMs) / 3600000;
    if (ageHours > o.staleHours) reasons.push(`마지막 성공이 ${ageHours.toFixed(1)}시간 전 (기준 ${o.staleHours}시간)`);
  }
  if (reasons.length > 0) return { state: "alert", reasons };
  if (lastMs == null) {
    return { state: "unknown", reasons: ["재단 성공 이력 없음 (막 배포했거나 첫 동기화 전)"] };
  }
  return { state: "ok", reasons: [] };
}

async function fetchStatus(fetchFn, url, tries, waitMs, sleep, log) {
  for (let i = 1; i <= tries; i++) {
    try {
      const res = await fetchFn(url, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(15000) });
      if (res.ok) return await res.json();
      log(`시도 ${i}/${tries}: HTTP ${res.status}`);
    } catch (e) {
      log(`시도 ${i}/${tries}: ${e && e.name ? e.name : "오류"}`);
    }
    if (i < tries) await sleep(waitMs);
  }
  return null;
}

function issueBody(status, result, url, runUrl, now) {
  const value = (v) => (v == null ? "없음" : String(v));
  return [
    "재단(수원문화재단) 행사 동기화가 계속 실패하고 있음.",
    "",
    ...result.reasons.map((r) => `- ${r}`),
    `- 마지막 성공(한국 시간): ${value(status.lastSuccessAt)}`,
    `- 연속 실패: ${value(status.consecutiveFailures)}회`,
    `- 확인 시각: ${now}`,
    `- 주소: ${url}`,
    `- 실행 기록: ${runUrl}`,
    "",
    "### 확인 방법 (EC2)",
    "- 앱 로그: `docker compose logs --since 24h app | grep -E '동기화|SWCF'`",
    "- 이력: `SELECT * FROM sync_log WHERE source = 'SWCF' ORDER BY id DESC LIMIT 5;` (error_message = 예외 종류)",
    "",
    "### 기록할 것",
    "- 원인:",
    "- 조치:",
    "- 재발 방지:",
    "",
    "다음 동기화가 성공하면 이 Issue 는 자동으로 닫힘.",
  ].join("\n");
}

// deps: { fetchFn, gh(args) → stdout 문자열(실패면 throw), env, nowMs, sleep, log }
async function main(deps) {
  const env = deps.env || {};
  const log = deps.log || console.log;
  const url = env.SYNC_STATUS_URL || DEFAULTS.url;
  const opts = {
    failLimit: Number(env.FAIL_LIMIT) || DEFAULTS.failLimit,
    staleHours: Number(env.STALE_HOURS) || DEFAULTS.staleHours,
  };
  const tries = Number(env.TRIES) || DEFAULTS.tries;
  const waitMs = env.WAIT_MS != null && env.WAIT_MS !== "" ? Number(env.WAIT_MS) : DEFAULTS.waitMs;
  const runUrl = `${env.GITHUB_SERVER_URL || "https://github.com"}/${env.GITHUB_REPOSITORY || ""}/actions/runs/${env.GITHUB_RUN_ID || ""}`;
  const now = formatKst(deps.nowMs);

  const status = await fetchStatus(deps.fetchFn, url, tries, waitMs, deps.sleep, log);
  const result = decide(status, deps.nowMs, opts);
  log(`판단: ${result.state}${result.reasons.length ? " — " + result.reasons.join(" · ") : ""}`);
  if (result.state === "unknown") return 0;

  // 조회가 실패하면 열린 Issue 가 있는지 모름 → 중복 생성 방지를 위해 아무것도 안 하고 끝냄
  let openIssue;
  try {
    openIssue = deps.gh(["issue", "list", "--label", LABEL, "--state", "open", "--json", "number", "--jq", ".[0].number // empty"]).trim();
  } catch (e) {
    log("동기화 Issue 조회 실패 — Issue 를 건드리지 않고 종료");
    return 2;
  }

  if (result.state === "ok") {
    if (!openIssue) {
      log(`정상: 마지막 성공 ${status.lastSuccessAt}, 연속 실패 ${status.consecutiveFailures ?? "모름"}`);
      return 0;
    }
    try {
      deps.gh(["issue", "comment", openIssue, "--body",
        `✅ 복구 확인: ${now} — 마지막 성공 ${status.lastSuccessAt} ([실행 기록](${runUrl}))`]);
      deps.gh(["issue", "close", openIssue]);
    } catch (e) {
      log(`Issue #${openIssue} 닫기 실패`);
      return 2;
    }
    log(`복구됨 → Issue #${openIssue} 닫음`);
    return 0;
  }

  // alert
  if (openIssue) {
    log(`이미 열린 동기화 Issue #${openIssue} 가 있어 새로 만들지 않음 (알림 반복 방지로 성공 처리)`);
    return 0;
  }
  deps.gh(["label", "create", LABEL, "--color", "D93F0B", "--description", "재단 동기화 실패 자동 알림", "--force"]);
  deps.gh(["issue", "create", "--label", LABEL, "--title", `⚠️ 재단 동기화 실패 (${now})`,
    "--body", issueBody(status, result, url, runUrl, now)]);
  log("동기화 실패 → Issue 생성");
  return 1;
}

module.exports = { decide, parseKst, formatKst, issueBody, main, LABEL };

if (require.main === module) {
  const { execFileSync } = require("node:child_process");
  main({
    fetchFn: fetch,
    gh: (args) => execFileSync("gh", args, { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"] }),
    env: process.env,
    nowMs: Date.now(),
    sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
  }).then(
    (code) => { process.exitCode = code; },
    (e) => { console.error(`실행 오류: ${e && e.message}`); process.exitCode = 2; },
  );
}
