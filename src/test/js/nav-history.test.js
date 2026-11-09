// 뒤로 가기 기록 시험 — 가짜 history 로 쌓기 · 되돌리기 · 덮어쓰기 · 주소 확인
const test = require("node:test");
const assert = require("node:assert");
const { createNavHistory } = require("../../main/resources/static/nav-history.js");

// 브라우저 history 흉내: pushState 는 앞 칸을 지움, go(n) 은 나중에(deliver) popstate 를 보냄
function setup({ state = null, url = "/", entries = null } = {}) {
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
    scrollRestoration: "auto",
  };
  const nav = createNavHistory({
    history: h,
    getUrl: () => page.url,
    currentUrl: () => h.entries[h.index].url,
    onBack: (layer) => {
      closed.push(layer);
      nav.close(layer); // 화면 닫기 함수는 nav.close 도 부름 (이미 빠진 칸이라 그대로여야 함)
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
        h.index += h.pendingGo.shift();
        nav.onPopState({ state: h.state });
      }
    }
  }
  function runTasks() {
    while (tasks.length) tasks.shift()();
  }
  // 사용자가 브라우저 뒤로 가기 / 앞으로 가기
  function userGo(n) {
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
  assert.deepStrictEqual(top().state, { nav: ["tab"] });

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
  assert.deepStrictEqual(h.state, { nav: [] });
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
  assert.deepStrictEqual(h.state, { nav: ["tab"] });
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
  assert.deepStrictEqual(h.state, { nav: ["tab"] });
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
      { state: { nav: [] }, url: "/" },
      { state: { nav: ["tab"] }, url: "/" },
      { state: { nav: ["tab", "detail"] }, url: "/?event=5" },
      { state: { nav: ["tab", "detail", "route"] }, url: "/?event=5&route=1" },
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
  assert.deepStrictEqual(h.state, { nav: [] });
  assert.deepStrictEqual(nav.layers(), []);
  assert.strictEqual(h.scrollRestoration, "manual");
});

test("앞으로 가기로 닫힌 칸에 오면 다시 열지 않고 지금 화면에 맞춤 (더 뒤로 가지 않음)", () => {
  const { h, nav, closed, flush, userGo } = setup();
  nav.start(false);
  nav.open("detail");
  flush();
  userGo(-1);
  userGo(1);
  assert.deepStrictEqual(closed, ["detail"]);
  assert.deepStrictEqual(h.pendingGo, []);
  assert.strictEqual(h.index, 1);
  assert.deepStrictEqual(h.state, { nav: [] });
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
  assert.deepStrictEqual(h.state, { nav: ["detail"] });
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
  assert.deepStrictEqual(h.state, { nav: [] });
  assert.deepStrictEqual(nav.layers(), []);
});
