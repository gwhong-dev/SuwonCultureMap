// ?error=state     : 보안 확인(state) 실패 — 오래된 탭·다른 사이트에서 연 로그인 등
// ?error=cancelled : 동의 화면에서 취소
(function () {
  I18n.apply(document); // 화면 언어 (i18n.js)
  var messages = {
    state: I18n.t("login.errorState"),
    cancelled: I18n.t("login.errorCancelled"),
  };
  var code = new URLSearchParams(location.search).get("error");
  if (code && messages[code]) {
    var el = document.getElementById("loginError");
    el.textContent = messages[code];
    el.hidden = false;
  }
})();
