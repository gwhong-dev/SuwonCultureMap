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

    document.getElementById("memberIdText").textContent = "회원 #" + data.memberId;
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
          ? '<span class="linked-badge">✓ 연동됨</span>'
          : '<a class="link-btn ' +
            provider.key +
            '" href="' +
            provider.authUrl +
            '">연동하기</a>');

      list.appendChild(row);
    });
  });

// ===== 닉네임 (11/13 확정 시안 P5) =====
// 저장된 닉네임 = 위 이름 줄 + 입력칸. 규칙 · 중복 확인은 서버가 하고 400 이면 서버 문구를 그대로 보여 줌
var savedNickname = "";

function showNickname(nickname) {
  savedNickname = nickname;
  document.getElementById("profileNickname").textContent = nickname || "닉네임 없음";
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
      setNicknameStatus("새 이름을 뽑았어요. 저장을 눌러야 바뀌어요.", "");
    })
    .catch(function () {
      setNicknameStatus("이름을 뽑지 못했어요. 잠시 뒤 다시 시도해 주세요.", "error");
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
    setNicknameStatus("지금 쓰는 닉네임이에요.", "");
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
      setNicknameStatus("닉네임을 저장했어요.", "ok");
    })
    .catch(function (err) {
      setNicknameStatus(err.message || "저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.", "error");
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
      alert("로그아웃하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
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
