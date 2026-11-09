// 창 포커스 이동 (11/9, #89 검증 지적) — 보조기기(스크린 리더 · 키보드) 사용자용
//  - 상세 · 길찾기 · 공유 창이 열리면 창 안의 첫 버튼으로 포커스를 옮김 (창이 열린 걸 알 수 있게)
//  - 닫히면 창을 열기 전에 포커스가 있던 자리(누른 카드 · 버튼)로 되돌림
//  - 이미 열린 창을 다시 열면(상세에서 다른 행사) 처음 자리를 그대로 기억
//  - 되돌릴 자리가 사라졌거나 숨겨졌으면(목록을 다시 그림 등) 되돌리지 않음
// 시험: node --test src/test/js/ (CI 에서 실행)

var FOCUSABLE = 'button, a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

// 화면에 보이는 요소인가 (display:none · hidden 안쪽이면 getClientRects 가 비어 있음)
function isShown(el) {
  return !!el && el.isConnected !== false && !el.disabled && el.getClientRects().length > 0;
}

// 창 안에서 처음 보이는 버튼 · 링크
function firstFocusable(container) {
  var list = container.querySelectorAll(FOCUSABLE);
  for (var i = 0; i < list.length; i++) {
    if (isShown(list[i])) return list[i];
  }
  return null;
}

function createDialogFocus(options) {
  var doc = options.document;
  var returnTo = {}; // 창 이름 → 열기 전 포커스 자리

  function focusEl(el) {
    try {
      el.focus({ preventScroll: true }); // 상세를 닫으며 맞춘 목록 스크롤이 튀지 않게
    } catch (e) {
      el.focus();
    }
  }

  return {
    // name 창이 container 로 열림
    opened: function (name, container) {
      if (!(name in returnTo)) {
        var active = doc.activeElement;
        returnTo[name] = active && active !== doc.body && !container.contains(active) ? active : null;
      }
      var first = firstFocusable(container);
      if (first) focusEl(first);
    },
    // name 창이 닫힘 (열려 있지 않았으면 아무것도 안 함)
    closed: function (name) {
      if (!(name in returnTo)) return;
      var target = returnTo[name];
      delete returnTo[name];
      if (isShown(target)) focusEl(target);
    },
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { firstFocusable: firstFocusable, createDialogFocus: createDialogFocus };
}
