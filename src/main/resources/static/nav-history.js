// 뒤로 가기 기록 (11/9) — 한 화면(index.html) 안에서 바뀌는 화면을 브라우저 뒤로 가기로 되돌림
// 칸 이름: "tab"(모바일 홈 → 지도 · 목록 · 찜) · "search"(검색 화면) · "detail"(상세) · "route"(길찾기 창) · "share"(공유 창)
//  - 화면이 열리면 기록 칸을 하나 쌓음(pushState). 이미 열린 칸이면 그대로 (지도 ↔ 목록 ↔ 찜은 "tab" 한 칸)
//  - 뒤로 가기(popstate) → 맨 위 칸부터 onBack(칸) 으로 그 화면을 닫음. 화면이 하나도 안 바뀌었으면(새로 고침 · 앞으로 가기로
//    남은 칸 등) 한 칸 더 뒤로 → 헛누름 없음. 앞으로 가기는 화면을 다시 열지 않음
//  - 화면의 ✕ · 덮개 · Esc 로 닫으면 화면은 바로 닫고, 쌓아 둔 칸은 history.go(-n) 로 되돌림
//    (닫자마자 다른 칸을 여는 경우는 맨 위 칸을 덮어써서 기다림 없이 끝냄)
//  - 칸마다 그때 주소(?event= · &route=1)를 같이 기록 → 뒤로 가면 주소도 그 화면 것으로
// 시험: node --test src/test/js/ (CI 에서 실행)

function createNavHistory(options) {
  var history = options.history;
  var getUrl = options.getUrl; // 지금 화면이 가져야 할 주소
  var currentUrl = options.currentUrl; // 브라우저에 지금 보이는 주소
  var onBack = options.onBack; // 뒤로 가기로 빠진 칸 → 그 화면 닫기. 실제로 닫았으면 true (이미 닫혀 있었으면 false)
  var schedule =
    options.schedule ||
    function (fn) {
      Promise.resolve().then(fn);
    };
  var setTimer = options.setTimer || setTimeout;
  var clearTimer = options.clearTimer || clearTimeout;
  var GO_WAIT = 1000; // go(-n) 뒤 popstate 를 기다리는 최대 시간

  var stack = []; // 지금 열린 칸 (아래 → 위)
  var queued = false;
  var waitTimer = null; // 우리가 부른 go(-n) 의 popstate 를 기다리는 중
  var lastN = 0; // 지금 칸의 순번 (칸마다 n 을 기록 → popstate 가 뒤로인지 앞으로인지 앎)

  function curN() {
    var state = history.state;
    return state && typeof state.n === "number" ? state.n : 0;
  }
  function stateOf(list, n) {
    return { nav: list.slice(), n: n };
  }
  function committed() {
    var state = history.state;
    return state && Array.isArray(state.nav) ? state.nav : [];
  }
  function commonLength(a, b) {
    var i = 0;
    while (i < a.length && i < b.length && a[i] === b[i]) i++;
    return i;
  }
  function sameList(a, b) {
    return a.length === b.length && commonLength(a, b) === a.length;
  }

  function queue() {
    if (queued) return;
    queued = true;
    schedule(sync);
  }

  // 브라우저 기록을 stack 에 맞춤
  function sync() {
    queued = false;
    if (waitTimer) return; // popstate 가 오면 다시 맞춤
    var from = committed();
    var keep = commonLength(from, stack);
    var back = from.length - keep; // 되돌릴 칸
    var add = stack.length - keep; // 새로 쌓을 칸
    var steps = add > 0 ? back - 1 : back; // 쌓을 칸이 있으면 맨 위 한 칸은 되돌리지 않고 덮어씀
    if (steps > 0) {
      waitTimer = setTimer(function () {
        waitTimer = null;
        write(keep, true); // popstate 가 안 오면(기록이 어긋남) 지금 칸을 덮어써서 맞춤
      }, GO_WAIT);
      history.go(-steps);
      return;
    }
    write(keep, back > 0);
  }

  function write(keep, replaceTop) {
    var start = keep;
    if (replaceTop && keep < stack.length) {
      history.replaceState(stateOf(stack.slice(0, keep + 1), curN()), "", getUrl());
      start = keep + 1;
    }
    for (var i = start; i < stack.length; i++) {
      history.pushState(stateOf(stack.slice(0, i + 1), curN() + 1), "", getUrl());
    }
    // 칸은 그대로인데 주소만 다름 (상세에서 다른 행사로 · 공유 링크로 들어와 상세를 닫음 등)
    if (!sameList(committed(), stack) || currentUrl() !== getUrl()) {
      history.replaceState(stateOf(stack, curN()), "", getUrl());
    }
    lastN = curN();
  }

  function onPopState(event) {
    if (waitTimer) {
      // 우리가 부른 go(-n) 이 끝남 → 남은 쌓기 · 덮어쓰기
      clearTimer(waitTimer);
      waitTimer = null;
      sync();
      return;
    }
    var target = committed();
    var n = curN();
    var back = n < lastN;
    lastN = n;
    if (!back) {
      // 앞으로 가기: 화면을 다시 열지도 닫지도 않고 그 칸을 지금 화면에 맞춤
      if (!sameList(target, stack)) history.replaceState(stateOf(stack, n), "", getUrl());
      return;
    }
    var keep = commonLength(stack, target);
    var changed = false;
    while (stack.length > keep) {
      if (onBack(stack.pop())) changed = true; // 맨 위 칸부터 닫음
    }
    // 지금 화면보다 긴 칸(새로 고침 · 앞으로 가기로 남은 칸)이면 다시 열지 않고 그 칸을 지금 화면에 맞춤
    if (target.length > stack.length) history.replaceState(stateOf(stack, n), "", getUrl());
    if (!changed) history.back(); // 화면이 그대로면 헛누름 → 한 칸 더 뒤로
  }

  return {
    // 처음 한 번: keep = 지금 기록 칸을 이어받음(지도 앱 · 카카오톡에 갔다 와서 ?event= 로 복원할 때), 아니면 비움
    start: function (keep) {
      if ("scrollRestoration" in history) history.scrollRestoration = "manual"; // 스크롤은 화면 코드가 맞춤
      if (keep) {
        stack = committed().slice();
      } else {
        stack = [];
        history.replaceState(stateOf(stack, curN()), "", currentUrl());
      }
      lastN = curN();
    },
    open: function (layer) {
      if (stack.indexOf(layer) !== -1) return;
      stack.push(layer);
      queue();
    },
    // 그 칸을 닫음. 위에 열린 칸이 있으면 그 화면부터 onBack 으로 닫음 (없으면 그대로 — 뒤로 가기로 이미 빠진 칸)
    close: function (layer) {
      var i = stack.lastIndexOf(layer);
      if (i === -1) return;
      while (stack.length > i + 1) onBack(stack.pop());
      stack.length = i;
      queue();
    },
    // 칸만 빼고 브라우저 기록은 다음에 맞춤 (카카오톡으로 넘어가는 중 — 바로 go(-1) 하면 이동이 끊길 수 있음)
    forget: function (layer) {
      var i = stack.lastIndexOf(layer);
      if (i !== -1) stack.length = i;
    },
    update: queue, // 주소만 다시 맞춤 (?event= · &route=1)
    onPopState: onPopState,
    layers: function () {
      return stack.slice();
    },
  };
}

if (typeof module !== "undefined") {
  module.exports = { createNavHistory: createNavHistory };
}
