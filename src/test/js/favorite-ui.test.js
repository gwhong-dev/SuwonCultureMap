// 찜 안내 문구 시험 — 토스트 · 찜 탭 빈 상태 (찜은 로그인 회원만)
const test = require("node:test");
const assert = require("node:assert");
const {
  favoriteEmptyState,
  favoriteToast,
  FAVORITE_LOGIN_TOAST,
  favoriteErrorText,
  isLoginRequiredError,
} = require("../../main/resources/static/favorite-ui.js");

test("빈 찜 탭: 로그인 안 했으면 로그인 버튼 (찜 개수와 상관없이)", () => {
  const state = favoriteEmptyState(false, 0);
  assert.strictEqual(state.title, "로그인하고 찜해 보세요");
  assert.strictEqual(state.action, "login");
  assert.strictEqual(state.actionLabel, "로그인");
  assert.strictEqual(favoriteEmptyState(false, 3).action, "login");
});

test("빈 찜 탭: 로그인했는데 찜이 0개면 행사 둘러보기", () => {
  const state = favoriteEmptyState(true, 0);
  assert.strictEqual(state.title, "아직 찜한 행사가 없어요");
  assert.strictEqual(state.action, "browse");
  assert.strictEqual(state.actionLabel, "행사 둘러보기");
});

test("빈 찜 탭: 찜은 있는데 검색 · 날짜 · 지난 행사 조건에 걸러졌으면 조건 확인 안내 (버튼 없음)", () => {
  const state = favoriteEmptyState(true, 2);
  assert.strictEqual(state.title, "조건에 맞는 찜한 행사가 없어요");
  assert.ok(state.body.includes("지난 행사"));
  assert.strictEqual(state.action, null);
  assert.strictEqual(state.actionLabel, null);
});

test("찜 토스트: 담으면 '보기' 버튼, 해제는 글만", () => {
  assert.deepStrictEqual(favoriteToast(true), { text: "찜 목록에 담았어요", actionLabel: "보기" });
  assert.deepStrictEqual(favoriteToast(false), { text: "찜을 해제했어요", actionLabel: null });
});

test("비로그인 ♡ 토스트: 로그인 버튼", () => {
  assert.deepStrictEqual(FAVORITE_LOGIN_TOAST, { text: "로그인하면 찜할 수 있어요", actionLabel: "로그인" });
});

test("실패 토스트: 서버 안내 글은 그대로, 비었거나 HTML · 긴 본문이면 기본 문구", () => {
  const fallback = "찜을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요";
  assert.strictEqual(favoriteErrorText("이미 즐겨찾기한 행사입니다."), "이미 즐겨찾기한 행사입니다.");
  assert.strictEqual(favoriteErrorText("  로그인이 필요합니다.\n"), "로그인이 필요합니다.");
  assert.strictEqual(favoriteErrorText(""), fallback);
  assert.strictEqual(favoriteErrorText(null), fallback);
  assert.strictEqual(favoriteErrorText("<html><body>502 Bad Gateway</body></html>"), fallback);
  assert.strictEqual(favoriteErrorText("가".repeat(61)), fallback);
});

test("세션 만료 판별: 서버의 '로그인이 필요합니다.' 만 (다른 오류 글은 아님)", () => {
  assert.strictEqual(isLoginRequiredError("로그인이 필요합니다."), true);
  assert.strictEqual(isLoginRequiredError(" 로그인이 필요합니다.\n"), true);
  assert.strictEqual(isLoginRequiredError("이미 즐겨찾기한 행사입니다."), false);
  assert.strictEqual(isLoginRequiredError(""), false);
  assert.strictEqual(isLoginRequiredError(null), false);
});
