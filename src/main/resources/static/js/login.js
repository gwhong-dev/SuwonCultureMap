// ?error=state     : 보안 확인(state) 실패 — 오래된 탭·다른 사이트에서 연 로그인 등
// ?error=cancelled : 동의 화면에서 취소
(function () {
  var messages = {
    state: "보안 확인에 실패했어요. 다시 로그인해 주세요.",
    cancelled: "로그인이 취소됐어요.",
  };
  var code = new URLSearchParams(location.search).get("error");
  if (code && messages[code]) {
    var el = document.getElementById("loginError");
    el.textContent = messages[code];
    el.hidden = false;
  }
})();
