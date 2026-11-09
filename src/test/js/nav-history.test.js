// 뒤로 가기 기록 시험 — 가짜 history 로 쌓기 · 되돌리기 · 덮어쓰기 · 주소 확인
const test = require("node:test");
const assert = require("node:assert");
const { createNavHistory } = require("../../main/resources/static/nav-history.js");

// 브라우저 history 흉내: pushState 는 앞 칸을 지움, go(n) 은 나중에(deliver) popstate 를 보냄
function setup({ state = null, url = "/", entries = null, isOpen = () => true } = {}) {
  const tasks = [];
  const timers = [];
  const closed = [];
  const page = { url: "/" }; // 화면 코드가 원하는 주소 (getUrl)
  const h = {
    entries: entries || [{ state, url }],
    index: entries ? entries.length - 1 : 0,
    pendingGo: [],
    get state() {
      return this.entries[this.index].state;
    },
    pushState(s, _t, u) {
      this.entries.splice(this.index + 1);
      this.entries.push({ state: JSON.parse(JSON.stringify(s)), url: u });
      this.index++;
    },
    replaceState(s, _t, u) {
      this.entries[this.index] = { state: JSON.parse(JSON.stringify(s)), url: u };
    },
    go(n) {
      this.pendingGo.push(n);
    },
    back() {
      this.pendingGo.push(-1);
    },
    left: false, // 첫 칸보다 더 뒤로 → 사이트를 나감
    scrollRestoration: "auto",
  };
  const nav = createNavHistory({
    history: h,
    getUrl: () => page.url,
    currentUrl: () => h.entries[h.index].url,
    onBack: (layer) => {
      closed.push(layer);
      nav.close(layer); // 화면 닫기 함수는 nav.close 도 부름 (이미 빠진 칸이라 그대로여야 함)
      return isOpen(layer); // 실제로 열려 있던 화면이었나
    },
    schedule: (fn) => tasks.push(fn),
    setTimer: (fn) => {
      timers.push(fn);
      return timers.length;
    },
    clearTimer: (id) => {
      timers[id - 1] = null;
    },
  });
  // 예약된 맞추기 실행 + 우리가 부른 go(-n) 처리까지
  function flush() {
    while (tasks.length || h.pendingGo.length) {
      while (tasks.length) tasks.shift()();
      if (h.pendingGo.length) {
        const n = h.pendingGo.shift();
        if (h.index + n < 0) {
          h.left = true;
          continue;
        }
        h.index += n;
        nav.onPopState({ state: h.state });
      }
    }
  }
  function runTasks() {
    while (tasks.length) tasks.shift()();
  }
  // 사용자가 브라우저 뒤로 가기 / 앞으로 가기
  function userGo(n) {
    if (h.index + n < 0) {
      h.left = true;
      return;
    }
    h.index += n;
    nav.onPopState({ state: h.state });
    flush();
  }
  const top = () => h.entries[h.index];
  return { h, nav, page, closed, timers, flush, runTasks, userGo, top };
}

test("홈 → 지도: 칸 하나 쌓고, 뒤로 가기면 홈으로 (tab 닫힘)", () => {
  const { h, nav, closed, flush, userGo, top } = setup();
  nav.start(false);
  nav.open("tab");
  flush();
  assert.strictEqual(h.entries.length, 2);
  assert.deepStrictEqual(top().state.nav, ["tab"]);

  userGo(-1);
  assert.deepStrictEqual(closed, ["tab"]);
  assert.deepStrictEqual(nav.layers(), []);
  assert.strictEqual(h.index, 0);
});

test("지도 ↔ 목록 ↔ 찜은 칸을 더 쌓지 않음 → 뒤로 한 번이면 홈", () => {
  const { h, nav, flush, userGo, closed } = setup();
  nav.start(false);
  nav.open("tab");
  flush();
  nav.open("tab");
  nav.open("tab");
  flush();
  assert.strictEqual(h.entries.length, 2);
  userGo(-1);
  assert.deepStrictEqual(closed, ["tab"]);
});

test("하단 탭 '홈'(화면에서 닫음) → go(-1) 로 되돌리고 onBack 은 부르지 않음", () => {
  const { h, nav, closed, flush } = setup();
  nav.start(false);
  nav.open("tab");
  flush();
  nav.close("tab");
  assert.deepStrictEqual(h.pendingGo, []); // 바로가 아니라 예약 뒤
  flush();
  assert.strictEqual(h.index, 0);
  assert.deepStrictEqual(closed, []);
  assert.deepStrictEqual(h.state.nav, []);
});

test("상세 → 길찾기: 뒤로 가기 한 번에 한 칸씩 닫힘, 주소도 그 칸 것", () => {
  const { h, nav, page, closed, flush, userGo, top } = setup();
  nav.start(false);
  page.url = "/?event=5";
  nav.open("detail");
  flush();
  page.url = "/?event=5&route=1";
  nav.open("route");
  flush();
  assert.deepStrictEqual(h.entries.map((e) => e.url), ["/", "/?event=5", "/?event=5&route=1"]);

  page.url = "/?event=5"; // 화면 코드가 길찾기 창을 닫으면 주소가 이렇게 됨
  userGo(-1);
  assert.deepStrictEqual(closed, ["route"]);
  assert.strictEqual(top().url, "/?event=5");
  page.url = "/";
  userGo(-1);
  assert.deepStrictEqual(closed, ["route", "detail"]);
  assert.strictEqual(top().url, "/");
});

test("검색 화면에서 찜 '보기'(검색 닫고 찜 탭) → 되돌리지 않고 맨 위 칸을 덮어씀", () => {
  const { h, nav, flush } = setup();
  nav.start(false);
  nav.open("search");
  flush();
  nav.close("search");
  nav.open("tab");
  flush();
  assert.deepStrictEqual(h.pendingGo, []);
  assert.strictEqual(h.entries.length, 2);
  assert.deepStrictEqual(h.state.nav, ["tab"]);
});

test("검색 → 상세 위에서 찜 '보기' → 한 칸 되돌린 뒤 덮어씀 (뒤로 한 번이면 홈)", () => {
  const { h, nav, closed, flush, userGo } = setup();
  nav.start(false);
  nav.open("search");
  nav.open("detail");
  flush();
  assert.strictEqual(h.entries.length, 3);
  nav.close("detail");
  nav.close("search");
  nav.open("tab");
  flush();
  assert.strictEqual(h.index, 1);
  assert.deepStrictEqual(h.state.nav, ["tab"]);
  assert.deepStrictEqual(closed, []);
  userGo(-1);
  assert.deepStrictEqual(closed, ["tab"]);
});

test("✕ 로 상세를 닫으면 주소도 되돌아감 (공유 링크로 들어온 첫 칸도 ?event= 를 지움)", () => {
  const { h, nav, page, flush, top } = setup({ url: "/?event=5" });
  nav.start(true); // 공유 링크: 기록 칸 없음 → 이어받을 것도 없음
  page.url = "/?event=5";
  nav.open("detail");
  flush();
  assert.strictEqual(h.entries.length, 2);
  page.url = "/";
  nav.close("detail");
  flush();
  assert.strictEqual(h.index, 0);
  assert.strictEqual(top().url, "/");
});

test("지도 앱에 갔다 와서 새로 열림(?event=&route=1): 기록 칸을 이어받고 다시 쌓지 않음", () => {
  // 브라우저엔 앞 문서가 쌓은 칸이 그대로 남아 있음 (맨 위 칸에서 새로 열림)
  const { h, nav, page, closed, flush, userGo } = setup({
    entries: [
      { state: { nav: [], n: 0 }, url: "/" },
      { state: { nav: ["tab"], n: 1 }, url: "/" },
      { state: { nav: ["tab", "detail"], n: 2 }, url: "/?event=5" },
      { state: { nav: ["tab", "detail", "route"], n: 3 }, url: "/?event=5&route=1" },
    ],
  });
  nav.start(true);
  page.url = "/?event=5&route=1";
  nav.open("detail"); // 복원 코드가 상세 · 길찾기를 다시 엶
  nav.open("route");
  flush();
  assert.strictEqual(h.entries.length, 4);
  assert.deepStrictEqual(nav.layers(), ["tab", "detail", "route"]);
  userGo(-1);
  userGo(-1);
  userGo(-1);
  assert.deepStrictEqual(closed, ["route", "detail", "tab"]);
  assert.strictEqual(h.index, 0);
});

test("새로 고침 등 처음 시작(keep=false): 남은 칸을 비우고 스크롤은 화면 코드가 맞춤", () => {
  const { h, nav } = setup({ state: { nav: ["tab", "search"] }, url: "/" });
  nav.start(false);
  assert.deepStrictEqual(h.state.nav, []);
  assert.deepStrictEqual(nav.layers(), []);
  assert.strictEqual(h.scrollRestoration, "manual");
});

test("앞으로 가기로 닫힌 칸에 오면 다시 열지 않고 지금 화면에 맞춤 → 다음 뒤로는 헛누름 없이 나감", () => {
  const { h, nav, closed, flush, userGo } = setup();
  nav.start(false);
  nav.open("detail");
  flush();
  userGo(-1);
  userGo(1);
  assert.deepStrictEqual(closed, ["detail"]);
  assert.deepStrictEqual(h.pendingGo, []);
  assert.strictEqual(h.index, 1);
  assert.deepStrictEqual(h.state.nav, []);
  userGo(-1); // 화면이 그대로인 칸 → 한 칸 더 뒤로 = 사이트 나감
  assert.ok(h.left);
});

test("갈라진 칸으로 앞으로 가도 화면을 닫지 않음 (검색 → 상세 → 찜 '보기' 뒤, 검증 ⚠️)", () => {
  const { h, nav, closed, flush, userGo } = setup();
  nav.start(false);
  nav.open("search");
  nav.open("detail");
  flush();
  nav.close("detail");
  nav.close("search");
  nav.open("tab");
  flush();
  assert.strictEqual(h.entries.length, 3); // 앞쪽에 ["search", "detail"] 칸이 남아 있음
  closed.length = 0;
  userGo(1);
  assert.deepStrictEqual(closed, []); // 홈으로 가지 않음
  assert.deepStrictEqual(nav.layers(), ["tab"]);
  userGo(-1); // 같은 화면 칸 → 건너뛰고 첫 칸 → tab 닫힘(홈)
  assert.deepStrictEqual(closed, ["tab"]);
  assert.strictEqual(h.index, 0);
});

test("새로 고침 뒤 첫 뒤로 가기는 헛누름 없이 나감 (지도 탭에서 새로 고침 → 홈, 검증 ⚠️)", () => {
  const { h, nav, closed, userGo } = setup({
    entries: [
      { state: { nav: [], n: 0 }, url: "/" },
      { state: { nav: ["tab"], n: 1 }, url: "/" },
    ],
  });
  nav.start(false); // 새로 고침: 홈부터
  userGo(-1);
  assert.deepStrictEqual(closed, []);
  assert.ok(h.left);
});

test("이어받은 칸 중 다시 열지 못한 화면은 건너뜀 (검색 위 상세에서 지도 앱 → 돌아옴, 검증 ⚠️)", () => {
  const { h, nav, closed, userGo } = setup({
    entries: [
      { state: { nav: [], n: 0 }, url: "/" },
      { state: { nav: ["tab"], n: 1 }, url: "/" },
      { state: { nav: ["tab", "search"], n: 2 }, url: "/" },
      { state: { nav: ["tab", "search", "detail"], n: 3 }, url: "/?event=5" },
    ],
    isOpen: (layer) => layer !== "search", // 복원은 상세만 다시 엶
  });
  nav.start(true);
  userGo(-1);
  assert.deepStrictEqual(closed, ["detail"]);
  userGo(-1); // 검색은 안 열려 있음 → 건너뛰고 tab 까지
  assert.deepStrictEqual(closed, ["detail", "search", "tab"]);
  assert.strictEqual(h.index, 0);
});

test("아래 칸을 닫으면 위 화면부터 닫음 (길찾기 위에서 찜 '보기' → 상세 닫기, 검증 ⚠️)", () => {
  const { nav, closed, flush } = setup();
  nav.start(false);
  nav.open("tab");
  nav.open("detail");
  nav.open("route");
  flush();
  nav.close("detail");
  assert.deepStrictEqual(closed, ["route"]);
  assert.deepStrictEqual(nav.layers(), ["tab"]);
});

test("카카오톡 공유: forget 은 기록을 바로 건드리지 않고, 다음 맞추기 때 되돌림", () => {
  const { h, nav, flush } = setup();
  nav.start(false);
  nav.open("detail");
  nav.open("share");
  flush();
  nav.forget("share");
  flush();
  assert.strictEqual(h.index, 2); // 그대로 (카카오톡으로 넘어가는 이동을 끊지 않음)
  nav.update(); // 페이지로 돌아옴 (visibilitychange)
  flush();
  assert.strictEqual(h.index, 1);
  assert.deepStrictEqual(h.state.nav, ["detail"]);
});

test("카카오톡 공유 뒤 맞추기 전에 뒤로 가기 → 헛누름 없이 상세가 닫힘 (인앱 브라우저 등, 검증 ⚠️)", () => {
  const { h, nav, closed, flush, userGo } = setup();
  nav.start(false);
  nav.open("detail");
  nav.open("share");
  flush();
  nav.forget("share");
  userGo(-1); // ["detail"] 칸: 바뀐 화면 없음 → 한 칸 더 → 상세 닫힘
  assert.deepStrictEqual(closed, ["detail"]);
  assert.strictEqual(h.index, 0);
});

test("go(-n) 뒤 popstate 가 안 오면 1초 뒤 지금 칸을 덮어써서 맞춤", () => {
  const { h, nav, timers, flush, runTasks } = setup();
  nav.start(false);
  nav.open("search");
  nav.open("detail");
  flush();
  nav.close("search"); // 두 칸 모두 닫힘 → go(-2)
  runTasks();
  assert.deepStrictEqual(h.pendingGo, [-2]);
  h.pendingGo = []; // popstate 가 오지 않음
  timers.filter(Boolean).forEach((fn) => fn());
  assert.deepStrictEqual(h.state.nav, []);
  assert.deepStrictEqual(nav.layers(), []);
});
