// ===== 3개 소셜 제공자 정보 (로그인 시작 주소 — 실제 인증 URL은 서버가 만듦) =====
var PROVIDERS = [
  {
    key: "kakao",
    label: "카카오",
    authUrl:
      "/api/auth/kakao/login",
  },
  {
    key: "google",
    label: "구글",
    authUrl:
      "/api/auth/google/login",
  },
  {
    key: "naver",
    label: "네이버",
    authUrl:
      "/api/auth/naver/login",
  },
];

// ===== 로그인 상태 확인 후 연동된 소셜 목록 렌더링 =====
fetch("/api/auth/me")
  .then((r) => r.json())
  .then(function (data) {
    if (!data.loggedIn) {
      // 로그인 안 한 상태로 마이페이지 접근 시 로그인 화면으로 이동
      window.location.href = "login.html";
      return;
    }

    document.getElementById("memberIdText").textContent =
      "회원 번호: " + data.memberId;

    var linkedProviders = data.linkedProviders || [];
    var list = document.getElementById("providerList");

    PROVIDERS.forEach(function (provider) {
      var row = document.createElement("div");
      row.className = "provider-row";

      var isLinked = linkedProviders.includes(provider.key);

      row.innerHTML =
        '<span class="provider-name">' + provider.label + "</span>" +
        (isLinked
          ? '<span class="linked-badge">✓ 연동됨</span>'
          : '<a class="link-btn ' +
            provider.key +
            '" href="' +
            provider.authUrl +
            '">연동하기</a>');

      list.appendChild(row);
    });
  });

// ===== 회원 탈퇴: 되돌릴 수 없으므로 한 번 더 확인 =====
document.getElementById("withdrawBtn").addEventListener("click", function () {
  var ok = confirm(
    "탈퇴하면 찜 목록과 연동된 소셜 계정 정보가 모두 삭제되고 되돌릴 수 없어요.\n" +
      "카카오 연결은 자동으로 해제돼요. 구글 · 네이버 앱 연결은 각 계정 설정에서 직접 해제할 수 있어요.\n\n" +
      "탈퇴할까요?"
  );
  if (!ok) return;
  var btn = document.getElementById("withdrawBtn");
  btn.disabled = true; // 요청 중 다시 눌러 실패 안내가 뜨지 않게
  fetch("/api/members/me", { method: "DELETE" })
    .then(function (r) {
      return r.text().then(function (message) {
        if (!r.ok) throw new Error(r.status === 400 ? message : "");
        alert(message);
        window.location.href = "index.html";
      });
    })
    .catch(function (e) {
      btn.disabled = false;
      alert(e.message || "탈퇴하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    });
});

// 계정 연결 실패 안내 (서버가 /mypage.html?error=... 로 보냈을 때)
(function () {
  var messages = {
    state: "보안 확인에 실패해서 계정을 연결하지 못했어요. 다시 시도해 주세요.",
    cancelled: "계정 연결이 취소됐어요.",
  };
  var params = new URLSearchParams(location.search);
  var code = params.get("error");
  if (code && messages[code]) {
    alert(messages[code]);
    history.replaceState(null, "", location.pathname); // 새로고침 때 다시 안 뜨게 주소 정리
  }
})();
