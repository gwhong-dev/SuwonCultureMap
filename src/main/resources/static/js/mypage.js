// 화면 언어 (i18n.js): HTML 고정 글을 먼저 그 언어로
I18n.apply(document);

// ===== 3개 소셜 제공자 정보 (로그인 시작 주소 — 실제 인증 URL은 서버가 만듦) =====
var PROVIDERS = [
  {
    key: "kakao",
    label: I18n.t("provider.kakao"),
    authUrl:
      "/api/auth/kakao/login",
  },
  {
    key: "google",
    label: I18n.t("provider.google"),
    authUrl:
      "/api/auth/google/login",
  },
  {
    key: "naver",
    label: I18n.t("provider.naver"),
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

    document.getElementById("memberIdText").textContent = I18n.t("member.number", { n: data.memberId });
    showNickname(data.nickname || "");

    var linkedProviders = data.linkedProviders || [];
    var list = document.getElementById("providerList");

    PROVIDERS.forEach(function (provider) {
      var row = document.createElement("div");
      row.className = "provider-row";

      var isLinked = linkedProviders.includes(provider.key);

      row.innerHTML =
        '<span class="provider-name">' + provider.label + "</span>" +
        (isLinked
          ? '<span class="linked-badge">' + I18n.t("mypage.linkedBadge") + "</span>"
          : '<a class="link-btn ' +
            provider.key +
            '" href="' +
            provider.authUrl +
            '">' + I18n.t("mypage.link") + "</a>");

      list.appendChild(row);
    });
  });

// ===== 닉네임 (11/13 확정 시안 P5) =====
// 저장된 닉네임 = 위 이름 줄 + 입력칸. 규칙 · 중복 확인은 서버가 하고 400 이면 서버 문구를 그대로 보여 줌
var savedNickname = "";

function showNickname(nickname) {
  savedNickname = nickname;
  document.getElementById("profileNickname").textContent = nickname || I18n.t("mypage.noNickname");
  document.getElementById("profileInitial").textContent = nickname ? nickname.charAt(0) : "";
  document.getElementById("nicknameInput").value = nickname;
}

function setNicknameStatus(message, kind) {
  var el = document.getElementById("nicknameStatus");
  el.textContent = message;
  el.className = "nickname-status" + (kind ? " is-" + kind : "");
}

document.getElementById("nicknameReroll").addEventListener("click", function () {
  var btn = this;
  btn.disabled = true;
  fetch("/api/members/me/nickname/suggestion")
    .then(function (r) {
      if (!r.ok) throw new Error();
      return r.json();
    })
    .then(function (data) {
      document.getElementById("nicknameInput").value = data.nickname;
      setNicknameStatus(I18n.t("mypage.rerolled"), "");
    })
    .catch(function () {
      setNicknameStatus(I18n.t("mypage.rerollFailed"), "error");
    })
    .finally(function () {
      btn.disabled = false;
    });
});

document.getElementById("nicknameForm").addEventListener("submit", function (e) {
  e.preventDefault();
  var input = document.getElementById("nicknameInput");
  var save = document.getElementById("nicknameSave");
  if (input.value.trim() === savedNickname) {
    setNicknameStatus(I18n.t("mypage.sameNickname"), "");
    return;
  }
  save.disabled = true;
  fetch("/api/members/me/nickname", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nickname: input.value }),
  })
    .then(function (r) {
      if (r.ok) return r.json();
      return r.text().then(function (message) {
        throw new Error(r.status === 400 ? message : "");
      });
    })
    .then(function (data) {
      showNickname(data.nickname);
      setNicknameStatus(I18n.t("mypage.saved"), "ok");
    })
    .catch(function (err) {
      setNicknameStatus(err.message || I18n.t("mypage.saveFailed"), "error");
    })
    .finally(function () {
      save.disabled = false;
    });
});

// ===== 로그아웃 (11/13: 상단 바에서 빼고 마이페이지로) =====
document.getElementById("logoutBtn").addEventListener("click", function () {
  var btn = this;
  btn.disabled = true;
  fetch("/api/auth/logout", { method: "POST" })
    .then(function () {
      window.location.href = "index.html";
    })
    .catch(function () {
      btn.disabled = false;
      alert(I18n.t("mypage.logoutFailed"));
    });
});

// ===== 회원 탈퇴: 되돌릴 수 없으므로 한 번 더 확인 =====
document.getElementById("withdrawBtn").addEventListener("click", function () {
  var ok = confirm(I18n.t("mypage.withdrawConfirm"));
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
      alert(e.message || I18n.t("mypage.withdrawFailed"));
    });
});

// 계정 연결 실패 안내 (서버가 /mypage.html?error=... 로 보냈을 때)
(function () {
  var messages = {
    state: I18n.t("mypage.linkErrorState"),
    cancelled: I18n.t("mypage.linkErrorCancelled"),
  };
  var params = new URLSearchParams(location.search);
  var code = params.get("error");
  if (code && messages[code]) {
    alert(messages[code]);
    history.replaceState(null, "", location.pathname); // 새로고침 때 다시 안 뜨게 주소 정리
  }
})();
