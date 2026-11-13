// 화면 언어 (i18n.js): HTML 의 고정 글([data-i18n])을 먼저 그 언어로 — 아래 코드가 글을 읽거나 바꾸기 전에
I18n.apply(document);

// ===== 텍스트 정리 / 안전하게 화면에 넣기 =====
// 재단 데이터 제목에 "<일월동행>" 같은 꺾쇠가 들어 있어서,
// innerHTML에 그대로 넣으면 태그로 해석돼 글자가 사라짐 → 항상 이스케이프해서 넣음
function escapeHtml(text) {
  return String(text == null ? "" : text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// CSP(script-src)는 HTML 안의 onclick · onerror 를 막음 → innerHTML 로 만든 요소는 addEventListener 로 직접 연결
// 사진을 못 불러오면 깨진 그림 대신 빈칸
function hideOnError(img) {
  img.addEventListener("error", function () {
    img.style.visibility = "hidden";
  });
}

// "&#39;" 같은 HTML 엔티티를 원래 글자로 (textarea는 태그를 실행하지 않아 안전)
function decodeEntities(text) {
  var t = document.createElement("textarea");
  t.innerHTML = text;
  return t.value;
}

// 행사 데이터 한 번만 정리: 제목은 엔티티 복원, 정보는 <br />를 줄바꿈으로
function cleanEventText(event) {
  if (event.title) event.title = decodeEntities(event.title);
  if (event.info) {
    event.info = decodeEntities(event.info.replace(/<br\s*\/?>/gi, "\n"));
  }
}

var map;
var markerList = [];
var eventsLoaded = false; // 행사 목록을 받기 전엔 목록(스켈레톤 · 실패 안내)을 다시 그리지 않음
// 지도 · 목록 검색칸(상단 #searchInput · PC 검색 띠)에서 마지막 글자를 IME 로 조합하는 중인지 → 치는 중 규칙 (home-picks.js searchTypingMode)
var mapSearchComposing = false;
var mapSearchTyping = false; // 마지막 filterMarkers 가 치는 중 규칙을 썼나 → 목록에서 넓혀서만 맞는 행사를 아래로 (renderSidebar)

// ===== 모바일 레이아웃 상태값 (아래 '모바일 레이아웃' 영역에서 사용) =====
var mobileQuery = window.matchMedia("(max-width: 1024px)");
var initialParams = new URLSearchParams(location.search); // 처음 들어온 주소의 ?event= · route=

// 열린 창을 주소에 기록 (?event=번호[&route=1]) → 지도 앱 · 카카오톡에 갔다 와서 페이지가 다시 열려도 복원
function currentQuery() {
  var id = detailEvent ? detailEvent.id : routeEvent ? routeEvent.id : null;
  var query = id ? "?event=" + id + (routeEvent ? "&route=1" : "") : "";
  // ?lang= 은 저장소가 막힌 브라우저에서만 붙음 (i18n.js) → 창을 여닫아도 지우지 않고 이어 붙임
  var lang = initialParams.get("lang");
  if (lang && I18n.LANGS.indexOf(lang) >= 0) query += (query ? "&" : "?") + "lang=" + lang;
  return query;
}

// 뒤로 가기 기록 (nav-history.js): 홈 → 지도 · 목록 · 찜, 검색 화면, 상세, 길찾기 · 공유 창이 열리면 칸을 쌓고
// 브라우저 뒤로 가기면 맨 위 화면부터 닫음. 칸마다 그때 주소(?event=)를 같이 기록
var nav = createNavHistory({
  history: window.history,
  getUrl: function () {
    return location.pathname + currentQuery() + location.hash;
  },
  currentUrl: function () {
    return location.pathname + location.search + location.hash;
  },
  // 칸의 화면을 닫음. 실제로 열려 있어서 닫았으면 true (false 면 nav 가 헛누름으로 보고 한 칸 더 뒤로)
  onBack: function (layer) {
    var open = false;
    if (layer === "tab") {
      open = !homeOpen && mobileQuery.matches;
      if (open) showHome(); // 지도 · 목록 · 찜 → 홈
    } else if (layer === "search") {
      open = searchOpen;
      closeSearch();
    } else if (layer === "detail") {
      open = !!detailEvent;
      closeDetailModal();
    } else if (layer === "route") {
      open = !!routeEvent;
      closeRouteSheet();
    } else if (layer === "share") {
      open = !document.getElementById("shareSheet").hidden;
      closeShareSheet();
    }
    return open;
  },
});
window.addEventListener("popstate", nav.onPopState);
// 카카오톡 등에서 돌아오면 미뤄 둔 기록 칸을 맞춤 (nav.forget)
document.addEventListener("visibilitychange", function () {
  if (!document.hidden) nav.update();
});

// 상세 · 길찾기 · 공유 창: 열리면 첫 버튼으로, 닫히면 원래 자리로 포커스 (dialog-focus.js)
var dialogFocus = createDialogFocus({ document: document });

function syncUrlState() {
  nav.update();
}
var homeOpen = false; // 모바일 홈 화면(하단 탭 "홈")이 지도 위를 덮고 있나
var sheetState = "peek"; // "peek"(지도 탭 — 결과 바만) | "full"(목록 보기 — 지도를 다 덮음, P3)
var sheetY = 0; // 바텀시트의 현재 위치 (위에서부터 px)
var previewEventId = null; // 하단 카드로 보고 있는 행사 id
var previewOrigin = null; // 마커를 탭한 행사 — 카드 순서는 여기서 가까운 순
var lastSortedItems = []; // 마지막으로 그린 목록 (카드 순서를 다시 매길 때 사용)
var selectedLocMarker = null; // 크게 표시 중인 장소 마커

// ===== 카테고리별 마커 아이콘 =====
var CATEGORY_STYLE = {
  행사: { color: "#4A90D9", emoji: "📅" },
  공연: { color: "#D9534F", emoji: "🎤" },
  전시: { color: "#8E44AD", emoji: "🎨" },
  축제: { color: "#F5A623", emoji: "🎉" },
  기타: { color: "#7F8C8D", emoji: "📌" },
};

// 찜 표시용 ★ 뱃지 (마커 왼쪽 위, 금색 — 목록·상세창의 ★과 같은 색)
function favoriteBadgeSvg(cx, cy) {
  return (
    '<circle cx="' + cx + '" cy="' + cy + '" r="8" fill="#F5A623" stroke="#fff" stroke-width="2"/>' +
    '<text x="' + cx + '" y="' + (cy + 4) + '" font-size="10" fill="#fff" text-anchor="middle">★</text>'
  );
}

// hasFavorite: 찜한 행사면 ★ 뱃지를 붙인 큰 그림판(40x40) 사용
function createMarkerIcon(category, hasFavorite) {
  var style = CATEGORY_STYLE[category] || CATEGORY_STYLE["기타"];
  var w = hasFavorite ? 40 : 36;
  var cx = hasFavorite ? 22 : 18; // ★ 뱃지 자리만큼 원을 오른쪽 아래로
  var cy = hasFavorite ? 22 : 18;
  var svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="' + w + '" height="' + w + '" viewBox="0 0 ' + w + " " + w + '">' + // viewBox: 선택된 마커를 크게 그릴 때 비율 유지
    '<circle cx="' + cx + '" cy="' + cy + '" r="16" fill="' +
    style.color +
    '" stroke="#fff" stroke-width="2"/>' +
    '<text x="' + cx + '" y="' + (cy + 6) + '" font-size="16" text-anchor="middle">' +
    style.emoji +
    "</text>" +
    (hasFavorite ? favoriteBadgeSvg(9, 9) : "") +
    "</svg>";
  return "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(svg);
}

// ===== 같은 좌표에 행사가 여러 개일 때 쓰는 "개수 뱃지" 마커 아이콘 =====
function createClusterMarkerIcon(count, hasFavorite) {
  var svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">' +
    '<circle cx="18" cy="20" r="16" fill="#34495E" stroke="#fff" stroke-width="2"/>' +
    '<text x="18" y="26" font-size="15" text-anchor="middle">📍</text>' +
    '<circle cx="31" cy="9" r="9" fill="#E74C3C" stroke="#fff" stroke-width="2"/>' +
    '<text x="31" y="13" font-size="11" font-weight="bold" fill="#fff" text-anchor="middle">' +
    count +
    "</text>" +
    (hasFavorite ? favoriteBadgeSvg(8, 31) : "") + // 묶음 안에 찜한 행사가 있으면 왼쪽 아래 ★
    "</svg>";
  return "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(svg);
}

// info 에서 장소명 뽑기(extractPlaceName · eventPlace)는 /event-detail.js

// ===== 이벤트 배열을 좌표(위치)별로 묶기 =====
function groupByLocation(items) {
  var groups = {};
  var order = [];
  items.forEach(function (item) {
    var key = item.event.latitude + "," + item.event.longitude;
    if (!groups[key]) {
      groups[key] = [];
      order.push(key);
    }
    groups[key].push(item);
  });
  return order.map(function (key) {
    return groups[key];
  });
}

// ===== 현재 지도에 떠 있는 "장소별 마커"들 (필터 바뀔 때마다 새로 그림) =====
var locationMarkers = [];

function renderLocationMarkers(visibleItems) {
  stopMarkerHop(); // 튀어오르던 마커 그림이 옛 자리에 남지 않게
  locationMarkers.forEach(function (lm) {
    lm.marker.setMap(null);
  });
  locationMarkers = [];

  groupByLocation(visibleItems).forEach(function (group) {
    var first = group[0].event;
    var position = new kakao.maps.LatLng(first.latitude, first.longitude);

    // 마커 그림 + 크기 (모바일에서 선택되면 1.33배로 키워서 강조)
    var groupHasFavorite = group.some(function (item) {
      return favoriteEventIds.has(item.event.id);
    });
    var iconSrc =
      group.length === 1
        ? createMarkerIcon(first.category, groupHasFavorite)
        : createClusterMarkerIcon(group.length, groupHasFavorite);
    var iconSize = group.length === 1 && !groupHasFavorite ? 36 : 40;
    var markerImage = new kakao.maps.MarkerImage(
      iconSrc,
      new kakao.maps.Size(iconSize, iconSize),
    );
    var bigSize = Math.round((iconSize * 4) / 3);
    var selectedImage = new kakao.maps.MarkerImage(
      iconSrc,
      new kakao.maps.Size(bigSize, bigSize),
    );

    var marker = new kakao.maps.Marker({
      position: position,
      image: markerImage,
      clickable: true, // 마커 클릭이 지도 클릭(미리보기 닫기)으로 번지지 않게
    });
    marker.setMap(map);

    // (하단 카드용) 같은 장소 행사들을 시작일 순으로 번호 매기기 → '같은 장소 1/3'
    var groupByDate = group.slice().sort(compareStartDate);
    groupByDate.forEach(function (item, i) {
      item.group = groupByDate;
      item.groupIndex = i;
      item.groupSize = groupByDate.length;
    });

    kakao.maps.event.addListener(marker, "click", function () {
      // 모바일 = 하단 카드, PC = 가운데 상세 칸 (11/13 Q4 D안). 같은 장소면 첫 번째부터
      if (mobileQuery.matches) showPreview(groupByDate[0].event.id);
      else pcSelect(groupByDate[0].event.id, { announce: true });
    });

    var locMarker = {
      marker: marker,
      normalImage: markerImage,
      selectedImage: selectedImage,
      iconSrc: iconSrc, // 같은 장소 카드 넘김 때 튀어오르는 그림 (hopMarker)
      bigSize: bigSize,
    };
    locationMarkers.push(locMarker);
    group.forEach(function (item) {
      item.locMarker = locMarker; // 행사 → 그 행사가 있는 장소 마커
    });
  });
}

// 시작일 오름차순 비교 compareStartDate 는 fav-sort.js (찜 날짜순과 같은 규칙)

// 지도를 처음 열었을 때의 기본 위치: 행궁동 중심 = 화성행궁 광장 (팔달구 정조로 825, 디자인 02)
// 좌표는 재단 API(XML)가 "화성행궁 광장 · 우화관 바깥마당" 행사에 주는 값과 같게 맞춤 → 그 장소 마커와 정확히 겹침
// level 6 첫 화면에 정조테마공연장 · 수원전통문화관 · 미디어센터 등 성곽 안 행사가 모두 들어옴 (11/9 운영 API 확인)
var DEFAULT_CENTER = { lat: 37.2818820191942, lng: 127.014394463798 };
// PC 첫 화면 (11/13 확정 B1 · B2 — docs/design/2026-11-13-pc-pending.md "③ 지도 · 키"):
//  - 축척 250m(레벨 5, pc-card.js PC_MAP_LEVEL). 전엔 네 곳(행궁 · 방화수류정 · 팔달문 · 지동시장) 범위로 맞춰 100m 로 좁았음
//  - 중심은 행사를 받은 뒤 상세 칸 첫 행사로 옮김 (pcAutoCenter). 받기 전 · 0건이면 화성행궁
//  - 모바일은 그대로 화성행궁 중심 level 6 (P3 담당)
kakao.maps.load(function () {
  var mapContainer = document.getElementById("map");
  var mapOption = {
    center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: mobileQuery.matches ? 6 : PC_MAP_LEVEL, // pc-card.js
  };
  map = new kakao.maps.Map(mapContainer, mapOption);
  setupPcZoom();

  // 지도 빈 곳을 눌러도 하단 카드 줄은 그대로 (PC 11/11 · 모바일 P3 — 지도 탭이면 카드 줄이 늘 떠 있음)
  guardMapDragFromTopbar();
  loadEvents();
});

// 상단 바(검색창 · 칩 줄)에서 시작한 터치 동안은 지도 끌기를 끔 (손을 떼면 다시 켬)
// 아이폰 사파리에서 칩 줄을 누른 채 지도 쪽으로 끌면 지도가 같이 끌렸음 (11/11, #208 의 CSS 수정으로는 안 고쳐짐).
// 터치가 어떤 요소로 가든 상관없게 위치로 판단하고, 카카오보다 먼저 보려고 document 캡처 단계에서 들음
function guardMapDragFromTopbar() {
  var guarded = false;
  function inside(el, x, y) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
  }
  document.addEventListener("touchstart", function (e) {
    if (e.touches.length !== 1) return;
    var t = e.touches[0];
    if (inside(document.getElementById("topbar"), t.clientX, t.clientY) ||
        inside(document.getElementById("categoryChips"), t.clientX, t.clientY)) {
      guarded = true;
      map.setDraggable(false);
    }
  }, { capture: true, passive: true });
  function release(e) {
    if (guarded && e.touches.length === 0) {
      guarded = false;
      map.setDraggable(true);
    }
  }
  document.addEventListener("touchend", release, { capture: true, passive: true });
  document.addEventListener("touchcancel", release, { capture: true, passive: true });
}

// (모바일) 행사를 받는 동안 목록 자리에 회색 블록 4장 — 목록이 그려지면 renderSidebar 가 지움
function renderSkeleton() {
  var list = document.getElementById("list");
  list.setAttribute("aria-busy", "true");
  var card =
    '<div class="skeleton-card" aria-hidden="true">' +
    '<div class="sk sk-thumb"></div>' +
    '<div class="sk sk-line w60"></div>' +
    '<div class="sk sk-line w40"></div>' +
    '<div class="sk sk-line w80"></div>' +
    "</div>";
  list.innerHTML = card + card + card + card;
}

// 실패 안내 (PC · 모바일 공통). kind: "network" = 서버에서 못 받음, "render" = 받았지만 화면에 그리다 오류
var LOAD_ERROR_TEXT = {
  network: I18n.t("load.networkError"),
  render: I18n.t("load.renderError"),
};

function renderLoadError(kind) {
  var list = document.getElementById("list");
  list.removeAttribute("aria-busy");
  list.innerHTML =
    '<div class="load-error" role="alert" data-kind="' + kind + '"><span>' + LOAD_ERROR_TEXT[kind] + "</span>" +
    '<button type="button" id="retryLoadBtn">' + I18n.t("common.retry") + "</button></div>";
  document.getElementById("retryLoadBtn").addEventListener("click", loadEvents);
  document.getElementById("resultCount").textContent = I18n.t("load.failedShort");
  updateSheetHeaderLabel();
}

var eventsLoadFailed = false; // 마지막 불러오기가 실패했나 (검색 화면 안내용)

function loadEvents() {
  eventsLoaded = false;
  eventsLoadFailed = false;
  renderSkeleton();
  document.getElementById("resultCount").textContent = I18n.t("load.loading");
  updateSheetHeaderLabel(); // 다시 시도 때 "불러오지 못했어요" 라벨이 남지 않게
  renderHomeLoading();
  pcDetailId = null;
  renderPcDetail(); // PC 상세 칸: 불러오는 중 (다시 시도 포함)
  fetch("/api/events")
    .then(function (response) {
      if (!response.ok) throw new Error("HTTP " + response.status);
      return response.json();
    })
    // 받기(fetch · JSON) 실패와 그리기 실패를 나눔 — 그리기 오류를 "네트워크" 안내로 보이지 않게
    .then(showEvents, function (err) {
      failLoad("network", err);
    })
    .catch(function (err) {
      failLoad("render", err);
    });
}

function failLoad(kind, err) {
  console.error(kind === "network" ? "행사 불러오기 실패" : "행사 표시 실패", err);
  renderLoadError(kind);
  renderHomeError();
  eventsLoadFailed = true;
  if (searchOpen) renderSearch();
  pcDetailId = null;
  renderPcDetail(); // PC 상세 칸: 불러오지 못했어요
}

function showEvents(events) {
  markerList = [];
  setupCategoryOptions(events);

  // 이 시점엔 마커를 만들지 않음. 실제 마커는 filterMarkers() -> renderLocationMarkers()에서
  // "장소별로 묶어서" 그림 (같은 좌표에 행사가 여러 개면 개수 뱃지 마커로)
  events.forEach(cleanEventText); // &#39; 복원, <br /> → 줄바꿈
  events.forEach(function (event) {
    markerList.push({ event: event });
  });
  eventsLoaded = true;
  // 이 기기 찜이 가득 찼을 때 밀어낼 번호(화면에 없는 행사)를 고르는 기준. 저장된 번호는 지우지 않음
  // (다른 행사에 합쳐 숨긴 번호도 로그인 때 서버가 대표 행사로 옮김)
  guestFavorites.setKnownIds(
    new Set(
      events.map(function (event) {
        return event.id;
      }),
    ),
  );

  filterMarkers();
  renderHome();
  if (searchOpen) renderSearch(); // 받는 중에 검색 화면을 열었으면 행을 채움

  // 공유 링크(?event=번호)로 들어오거나, 지도 앱에 갔다 와서 페이지가 다시 열리면 그 행사 상세를 복원
  // (길찾기 창까지 열려 있었으면 &route=1 → 길찾기 창도 다시 엶)
  var shared = Number(initialParams.get("event"));
  if (shared) {
    var found = markerList.find(function (item) {
      return item.event.id === shared;
    });
    if (found) {
      map.setCenter(new kakao.maps.LatLng(found.event.latitude, found.event.longitude));
      // 하단 카드에서 상세 없이 길찾기만 열었던 경우(이어받은 칸에 route 만 있음)는 길찾기만 다시 엶 → 칸 순서 = 화면 순서
      var inherited = nav.layers();
      var routeOnly = inherited.indexOf("route") !== -1 && inherited.indexOf("detail") === -1;
      if (!routeOnly) openDetailModal(found.event);
      if (initialParams.get("route") && mobileQuery.matches) openRouteSheet(found.event); // 길찾기 창은 모바일 전용
    }
  }
}

// ===== 카테고리 드롭다운 순서 고정 + "찜"(가상 카테고리) 추가 =====
var CATEGORY_ORDER = ["행사", "공연", "전시", "축제", "찜", "기타"];

function setupCategoryOptions(events) {
  var categories = [...new Set(events.map((e) => e.category))];
  var select = document.getElementById("categorySelect");
  // 다시 시도로 또 불러올 수 있으니 "전체" 뒤의 옵션은 지우고 새로 넣음
  Array.from(select.options).slice(1).forEach(function (option) {
    option.remove();
  });

  CATEGORY_ORDER.forEach(function (category) {
    // '찜'은 실제 DB 카테고리가 아니라 항상 넣어줌
    if (category === "찜" || categories.includes(category)) {
      var option = document.createElement("option");
      option.value = category;
      option.textContent = category === "찜" ? I18n.t("chip.favPlain") : I18n.name("cat", category); // 값은 원래 이름, 글자만 화면 언어
      select.appendChild(option);
    }
  });

  renderCategoryChips(); // 모바일용 칩도 같은 목록으로 생성
}

// ===== 지난 행사 판별 = 상태 배지의 "종료" 와 같은 기준 (한국 시간, schedule-format.js) =====
function isPastEvent(event) {
  return eventStatus(event) === "ended";
}

// 상태 배지 + D-day 줄 (모바일 전용 — PC 는 CSS 로 숨김). 날짜 없는 행사는 빈 문자열
function badgeRowHtml(event) {
  var status = eventStatus(event);
  if (!status) return "";
  var dday = ddayText(event);
  return (
    '<div class="badge-row">' +
    '<span class="status-badge ' + status + '">' + statusLabel(status) + "</span>" +
    (dday ? '<span class="dday-badge">' + dday + "</span>" : "") +
    "</div>"
  );
}

// 카드 날짜 줄: 날짜 필터에서 날짜를 모르는 행사면 "기간 중 · 일정 확인" 을 붙임
function cardDateText(item) {
  var text = formatEventDates(item.event);
  return item.runMatch === "maybe" ? text + " · " + MAYBE_RUN_NOTE : text;
}

// 장소 · 구 한 줄 (P3 모바일 카드 · 목록 줄 "정조테마공연장 · 팔달구"). 둘 다 모르면 빈 글
function placeLineHtml(event, className) {
  var text = [eventPlace(event), eventDistrict(event)].filter(Boolean).join(" · ");
  return text ? '<div class="' + className + '">' + escapeHtml(text) + "</div>" : "";
}

// 구 필터 (null = 전체). 홈 "내 주변"의 "지도에서 보기"가 켜고, 지도 칩 줄 맨 앞 "📍 ○○구 ✕" 칩으로 끔
var districtFilter = null;
// 무료만 (지도 칩 줄 "💸 무료", 한 번 더 누르면 해제). 판정은 event-detail.js isFreeEvent
var freeOnly = false;

function setDistrictFilter(district) {
  districtFilter = district || null;
  filterMarkers();
}

// ===== 필터링 (검색어 + 카테고리 + 진행 날짜 + 구, '찜'은 즐겨찾기 목록으로 필터) =====
function filterMarkers() {
  if (!eventsLoaded) return; // 아직 받는 중(스켈레톤) 또는 실패 안내 표시 중 — 로그인 확인 등이 지우지 않게
  syncPcSearch(); // PC 목록 칸 검색 띠 = 상단 바 #searchInput 값 (다른 곳에서 검색어를 지워도 맞춤)
  var keyword = document.getElementById("searchInput").value.trim();
  // 치는 중 규칙: 조합 중이면 마지막 글자를 넓혀 찾음 ("과" → 광복도), 끝난 글자는 그대로 없을 때만 (모바일 검색 화면과 같음)
  mapSearchTyping = keyword
    ? searchTypingMode(
        markerList.map(function (item) {
          return item.event;
        }),
        keyword,
        mapSearchComposing,
      )
    : false; // home-picks.js
  var selectedCategory = document.getElementById("categorySelect").value;
  var dateValue = document.getElementById("dateFilter").value;
  // 종료 행사는 기본으로 숨김. 지난 날짜를 고르면 그날 열린 행사(종료 포함)를 보여 줌 (calendar-strip.js, 11/13 P2)
  var hideEnded = hidesEndedEvents(dateValue);
  var runDays = filterDays(dateValue); // 한국 시간 기준, 필터 없으면 null
  var statusTab = currentStatusTab(); // 목록 위 탭 (진행 중 · 진행 예정), 날짜를 골랐으면 null
  // PC 분류 펼침 목록 개수 (R5 A4): 분류만 빼고 다른 조건을 통과한 행사. 진행 탭은 그 분류를 골랐을 때 걸리는지로 ('찜' 은 안 걸림)
  var countTab = statusTabApplies({ dateValue: dateValue, keyword: keyword, category: "all" }) ? pcStatusTab : null;
  var favCountTab = statusTabApplies({ dateValue: dateValue, keyword: keyword, category: "찜" }) ? pcStatusTab : null;
  var countEvents = [];
  var favCountEvents = [];

  updateChipActive(); // 모바일 칩 선택 표시 동기화
  updateTabBar(); // 모바일 하단 탭 선택 표시 동기화

  var visibleItems = [];
  var dateBaseItems = []; // 날짜 · 진행 탭만 빼고 나머지 조건을 통과한 행사 → 날짜 줄 · 달력 · 탭 개수
  var favScreen = onFavScreen();

  markerList.forEach(function (item) {
    if (favScreen) {
      // 찜 탭(모바일) = 따로 된 화면: 지도 쪽 조건(검색어 · 분류 · 날짜 · 구 · 지난 행사)과 상관없이 찜 전부 (종료는 배지)
      item.runMatch = null;
      if (favoriteEventIds.has(item.event.id)) visibleItems.push(item);
      return;
    }
    var matchesKeyword = matchesSearch(item.event, keyword, mapSearchTyping); // 띄어쓰기 · 대소문자 무시, 제목 + 장소 (home-picks.js)
    var matchesCategory;

    if (selectedCategory === "all") {
      matchesCategory = true;
    } else if (selectedCategory === "찜") {
      matchesCategory = favoriteEventIds.has(item.event.id);
    } else {
      matchesCategory = item.event.category === selectedCategory;
    }

    var matchesPast = !hideEnded || !isPastEvent(item.event);

    item.runMatch = runDays ? matchRunDays(item.event, runDays) : null;
    var matchesDate = item.runMatch !== "no";
    var matchesBase =
      matchesKeyword && matchesPast && matchesDate && matchesDistrict(item.event, districtFilter) && (!freeOnly || isFreeEvent(item.event));
    if (matchesBase && matchesStatusTab(item.event, countTab)) countEvents.push(item.event);
    if (matchesBase && matchesStatusTab(item.event, favCountTab)) favCountEvents.push(item.event);

    // 날짜 줄 · 달력 개수는 종료 행사도 셈 (지난 날 칸에도 그날 열린 행사 수가 보이게, 오늘 이후 칸엔 종료 행사가 안 걸림)
    var matchesOthers =
      matchesKeyword &&
      matchesCategory &&
      matchesDistrict(item.event, districtFilter) &&
      (!freeOnly || isFreeEvent(item.event));
    if (matchesOthers) dateBaseItems.push(item);

    if (matchesOthers && matchesPast && matchesDate && matchesStatusTab(item.event, statusTab)) {
      visibleItems.push(item);
    }
  });
  renderDatePanel(dateBaseItems, visibleItems.length, favScreen);
  if (!favScreen) {
    lastCategoryCounts = pcCategoryCounts(
      countEvents,
      function (id) {
        return favoriteEventIds.has(id);
      },
      favCountEvents,
    ); // pc-search.js
    renderCatList();
  }

  // 찜 탭(모바일) "최근 본": 위 조건 대신 최근 본 순서 그대로 (기록이라 검색어 · 날짜 · 지난 행사와 상관없이)
  if (!favScreen) favView = "fav"; // 찜 탭을 나가면 다음엔 '찜'부터
  var recentItems = recentMarkerItems();
  updateFavTabs(favScreen, visibleItems.length, recentItems.length);
  if (showingRecent()) {
    recentItems.forEach(function (item) {
      item.runMatch = null; // 날짜 필터 "기간 중 · 일정 확인" 표시도 빼고
    });
    visibleItems = recentItems;
  }

  renderLocationMarkers(visibleItems); // 장소별로 묶어서 마커 다시 그리기
  renderSidebar(visibleItems);
  updateSearchFavs();
  updateFavAlertDot();
}

// ===== 찜 알림 (화면 안 배지, favorite-alerts.js): 내일 시작 · 3일 안 종료 =====
// 찜 목록 맨 위 알림 칸 + 하단 '찜' 탭 빨간 점. 알림 칸을 보면 점이 꺼짐 (본 알림 = 이 기기 브라우저 저장소)
var alertSeenStore = createAlertSeenStore(
  (function () {
    try {
      return window.localStorage;
    } catch (e) {
      return null;
    }
  })(),
  "suwonbible.favAlertsSeen",
);

function currentFavAlerts(items) {
  return favoriteAlerts(
    (items || markerList).map(function (item) {
      return item.event;
    }),
    favoriteEventIds,
  );
}

function updateFavAlertDot() {
  var unseen = unseenAlertCount(currentFavAlerts(), alertSeenStore.list());
  var tab = document.querySelector('#tabBar .tab-item[data-tab="fav"]');
  document.getElementById("favTabDot").hidden = unseen === 0;
  if (unseen > 0) {
    tab.setAttribute("aria-label", I18n.t("alert.tabLabel", { n: unseen }));
  } else {
    tab.removeAttribute("aria-label");
  }
}

// 찜 목록 맨 위 알림 칸 = 지금 목록에 보이는 찜(PC 검색어 · 날짜 조건과 같게). 알림이 없으면 안 그림
// 화면에 보일 때만 본 것으로 기록 (shouldMarkAlertsSeen)
function renderFavAlerts(list, favoriteItems) {
  var alerts = currentFavAlerts(favoriteItems);
  if (!alerts.length) return;
  var box = document.createElement("section");
  box.className = "fav-alerts";
  box.setAttribute("aria-label", I18n.t("alert.boxLabel"));
  var title = document.createElement("strong");
  title.className = "fav-alerts-title";
  title.textContent = I18n.t("alert.title", { n: alerts.length });
  box.appendChild(title);
  var ul = document.createElement("ul");
  alerts.forEach(function (alert) {
    var li = document.createElement("li");
    var button = document.createElement("button");
    button.type = "button";
    var badge = document.createElement("span");
    badge.className = "fav-alert-badge " + alert.kind;
    badge.textContent = favoriteAlertLabel(alert);
    var name = document.createElement("span");
    name.className = "fav-alert-name";
    name.textContent = alert.event.title;
    button.append(badge, name);
    button.addEventListener("click", function () {
      openDetailModal(alert.event);
    });
    li.appendChild(button);
    ul.appendChild(li);
  });
  box.appendChild(ul);
  list.appendChild(box);
  if (shouldMarkAlertsSeen(mobileQuery.matches, onFavScreen())) alertSeenStore.markSeen(alerts);
}

// ===== 찜 탭 "최근 본" (디자인 05): 상세를 연 행사 번호만 이 기기 브라우저 저장소에 (recent-views.js) =====
var favView = "fav"; // 찜 탭 안: "fav" 찜 | "recent" 최근 본
// 저장소를 못 쓰는 브라우저(막힘 · 가득 참)에선 이 페이지에서만 기억. 접근만 해도 오류가 나는 브라우저가 있어 try
var recentStore = createRecentStore(
  (function () {
    try {
      return window.localStorage;
    } catch (e) {
      return null;
    }
  })(),
  "suwonbible.recentEvents",
);

function rememberRecentView(eventId) {
  recentStore.add(eventId);
  // '찜'을 보는 중이면 탭 숫자만 맞춤. 최근 본 목록은 다시 그리지 않음 (보던 자리가 밀리지 않게, 다음에 그릴 때 순서 · 숫자를 같이 맞춤)
  if (!document.getElementById("favTabs").hidden && !showingRecent()) {
    document.getElementById("favTabRecentCount").textContent = recentMarkerItems().length;
  }
}

// 최근 본 순서대로 지금 받아 온 행사 (지워진 행사는 건너뜀)
function recentMarkerItems() {
  return pickRecent(recentStore.list(), markerList, function (item) {
    return item.event.id;
  });
}

// 찜 탭(모바일)을 보는 중인가. 분류 드롭다운 값('찜' 칩 · PC '찜')과는 따로 — 탭을 오가도 지도 쪽 분류가 그대로 남게
var favTabOpen = false;

function onFavScreen() {
  return favTabOpen && mobileQuery.matches;
}

function showingRecent() {
  return favView === "recent" && onFavScreen();
}

// 찜 | 최근 본 탭: 찜 탭(모바일)에서만 보임, 개수 · 선택 표시
function updateFavTabs(visible, favCount, recentCount) {
  document.getElementById("favTabs").hidden = !visible;
  document.getElementById("favTabFavCount").textContent = favCount;
  document.getElementById("favTabRecentCount").textContent = recentCount;
  var list = document.getElementById("list");
  document.querySelectorAll("#favTabs .fav-tab").forEach(function (tab) {
    var active = tab.dataset.view === favView;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", active ? "true" : "false");
    tab.tabIndex = active ? 0 : -1;
    if (active) list.setAttribute("aria-labelledby", tab.id);
  });
  // 탭 줄이 있을 때만 목록 = 탭 내용 (보조기기)
  if (visible) {
    list.setAttribute("role", "tabpanel");
  } else {
    list.removeAttribute("role");
    list.removeAttribute("aria-labelledby");
  }
  // 찜 탭(찜 · 최근 본)엔 날짜 · 분류 · 지난 행사 칩이 걸리지 않음 → 흐리게 하고 못 누르게 (눌러도 목록이 그대로라 고장처럼 보여서)
  document.body.classList.toggle("fav-screen", visible);
  document.getElementById("categoryChips").toggleAttribute("inert", visible);
}

function selectFavView(view, focus) {
  if (favView === view) return;
  favView = view;
  hidePreview();
  filterMarkers();
  window.scrollTo(0, 0); // 목록 전체는 페이지 스크롤 → 새 목록 맨 위부터
  document.getElementById("list").scrollTop = 0; // 절반으로 내린 시트는 목록 상자 스크롤
  if (focus) document.querySelector('#favTabs .fav-tab[data-view="' + view + '"]').focus();
}

document.querySelectorAll("#favTabs .fav-tab").forEach(function (tab) {
  tab.addEventListener("click", function () {
    selectFavView(tab.dataset.view, false);
  });
  tab.addEventListener("keydown", function (e) {
    // 좌우 화살표로도 바꿈 (홈 곧 종료 · 곧 시작 탭과 같게)
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    selectFavView(favView === "fav" ? "recent" : "fav", true);
  });
});

// "모두 지우기" → 기록을 비우고 빈 안내. 누른 버튼이 사라지므로 초점은 "최근 본" 탭으로
function clearRecentViews() {
  recentStore.clear();
  filterMarkers();
  document.getElementById("favTabRecent").focus();
  showToast(I18n.t("recent.cleared"));
}

// ===== Side-bar용 info 요약 (요금/장소/시간만 추출) =====
// fee = 서버가 꺼낸 요금 칸(V7, 관람료 · 참가비용 포함). 없으면(동기화 전) 원문의 "이용료" 줄
function extractSummaryInfo(infoText, fee) {
  if (!infoText) return "";

  // "라벨: 내용" 형태의 줄들을 라벨 기준으로 매핑
  var map = {};
  infoText.split("\n").forEach(function (line) {
    var idx = line.indexOf(":");
    if (idx === -1) return;
    var label = line.substring(0, idx).trim();
    map[label] = line;
  });

  var result = [];

  // 1) 요금, 장소 순서로 먼저 추가
  if (fee) {
    result.push(I18n.t("detail.fee") + ": " + fee);
  } else if (map["이용료"]) {
    result.push(map["이용료"]);
  }
  if (map["장소"]) result.push(map["장소"]);

  // 2) 요일/시간 관련 라벨은 API마다 이름이 달라서 포함 여부로 탐색
  Object.keys(map).forEach(function (label) {
    if (label.indexOf("시간") !== -1 || label.indexOf("요일") !== -1) {
      if (result.indexOf(map[label]) === -1) result.push(map[label]);
    }
  });

  return result.join("\n");
}

// ===== 찜 탭이 비었을 때 안내 (비로그인 · 아직 없음 · 조건에 걸러짐) =====
var EMPTY_HEART_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" /></svg>';
var EMPTY_CLOCK_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="12" cy="12" r="8" /><path d="M12 8v4l3 2" /></svg>';

function renderFavoriteEmpty(list) {
  var favoriteCount = markerList.filter(function (item) {
    return favoriteEventIds.has(item.event.id);
  }).length;
  renderEmptyBox(list, favoriteEmptyState(isLoggedIn, favoriteCount), EMPTY_HEART_SVG);
}

// 하트(찜) · 시계(최근 본) + 제목 · 안내 · 버튼
function renderEmptyBox(list, state, iconSvg) {
  var box = document.createElement("div");
  box.className = "fav-empty";
  box.innerHTML = iconSvg + '<strong class="fav-empty-title"></strong><span class="fav-empty-body"></span>';
  box.querySelector(".fav-empty-title").textContent = state.title;
  box.querySelector(".fav-empty-body").textContent = state.body;
  if (state.action) {
    var button = document.createElement("button");
    button.type = "button";
    button.textContent = state.actionLabel;
    button.addEventListener("click", state.action === "login" ? goToLogin : browseAllEvents);
    box.appendChild(button);
  }
  list.appendChild(box);
}

// ===== 찜한 행사 정렬 [날짜순 | 이름순] (확정 시안 FavSortC, fav-sort.js) =====
// 고른 값은 이 기기 브라우저 저장소에만. 접근만 해도 오류가 나는 브라우저가 있어 try
var favSortStore = createFavSortStore(
  (function () {
    try {
      return window.localStorage;
    } catch (e) {
      return null;
    }
  })(),
  "suwonbible.favSort",
);
var lastSidebarItems = []; // 정렬 단추를 누르면 같은 목록을 다시 그림

// 찜 구분 제목 오른쪽 단추 묶음. 누르면 저장 → 목록 다시 그림 → 누른 단추에 초점을 돌려 둠 (키보드 사용자)
function favSortGroupHtml(mode) {
  return (
    '<div class="fav-sort" role="group" aria-label="' + escapeHtml(I18n.t("favSort.label")) + '">' +
    FAV_SORT_OPTIONS.map(function (option) {
      return (
        '<button type="button" class="fav-sort-btn" data-sort="' +
        option.value +
        '" aria-pressed="' +
        (option.value === mode) +
        '">' +
        option.label +
        "</button>"
      );
    }).join('<span class="fav-sort-sep" aria-hidden="true">|</span>') +
    "</div>"
  );
}

function changeFavSort(mode) {
  if (normalizeFavSort(mode) === favSortStore.get()) return;
  var next = favSortStore.set(mode);
  renderSidebar(lastSidebarItems);
  var pressed = document.querySelector('#list .fav-sort-btn[data-sort="' + next + '"]');
  if (pressed) pressed.focus({ preventScroll: true }); // 초점만 옮기고 보던 자리는 그대로
}

// ===== Side-bar 카드 렌더링 (이미지 + 제목 + info 요약) =====
function renderSidebar(items) {
  lastSidebarItems = items;
  var recent = showingRecent(); // 최근 본: 본 순서 그대로, 구분 제목 없음
  // 찜한 행사는 맨 위에 고른 정렬(기본 날짜순), 나머지는 시작일 순
  var favSort = favSortStore.get();
  var favorites = recent
    ? []
    : sortFavorites(
        items.filter(function (item) {
          return favoriteEventIds.has(item.event.id);
        }),
        favSort,
      );
  var others = recent
    ? items
    : items
        .filter(function (item) {
          return !favoriteEventIds.has(item.event.id);
        })
        .sort(compareStartDate);
  if (!recent && mapSearchTyping) {
    // 치는 중 규칙으로 넓혀서만 맞은 행사는 각 묶음 아래로 ("미" → 미술 먼저, 시민은 아래)
    var searchKeyword = document.getElementById("searchInput").value.trim();
    var eventOf = function (item) {
      return item.event;
    };
    favorites = searchLooseLast(favorites, searchKeyword, true, eventOf); // home-picks.js
    others = searchLooseLast(others, searchKeyword, true, eventOf);
  }
  var sorted = favorites.concat(others);

  var list = document.getElementById("list");
  var focusRowId = pcFocusedRowId(); // PC: 목록 줄에서 Space 찜 → 다시 그려도 같은 줄에 포커스 (키보드가 이어지게)
  list.innerHTML = "";
  list.removeAttribute("aria-busy"); // 스켈레톤 끝
  // PC 검색 중이면 목록 맨 위 구분 제목 "‘가족’ 검색 결과 N개" (R4 A — 찜 구분 제목과 같은 모양, 지우기는 검색 띠 ✕ 하나)
  var pcKeyword = document.getElementById("searchInput").value.trim();
  if (!recent && !mobileQuery.matches && pcKeyword) {
    var searchTitle = document.createElement("div");
    searchTitle.className = "list-section-title search-section-title";
    searchTitle.textContent = I18n.t("search.pcResults", { q: pcKeyword, n: sorted.length });
    list.appendChild(searchTitle);
  }
  if (recent) {
    if (sorted.length === 0) {
      renderEmptyBox(list, recentEmptyState(), EMPTY_CLOCK_SVG);
    } else {
      var note = document.createElement("div");
      note.className = "recent-note";
      note.innerHTML = "<span>" + I18n.t("recent.deviceOnly") + '</span><button type="button">' + I18n.t("recent.clearAll") + "</button>";
      note.querySelector("button").addEventListener("click", clearRecentViews);
      list.appendChild(note);
    }
  } else if (sorted.length === 0 && (onFavScreen() || document.getElementById("categorySelect").value === "찜")) {
    renderFavoriteEmpty(list);
  } else if (!isLoggedIn && (onFavScreen() || document.getElementById("categorySelect").value === "찜")) {
    // 비회원 찜 목록 맨 위: 이 기기에만 있다는 안내 + 로그인 (최근 본 안내와 같은 모양)
    var guestNote = document.createElement("div");
    guestNote.className = "recent-note guest-note";
    guestNote.innerHTML = "<span>" + I18n.t("fav.guestNote") + '</span><button type="button">' + I18n.t("nav.login") + "</button>";
    guestNote.querySelector("button").addEventListener("click", goToLogin);
    list.appendChild(guestNote);
  }

  // 찜 목록(모바일 찜 탭 '찜' · 분류 '찜')이면 맨 위에 곧 시작 · 곧 종료 알림
  if (!recent && favorites.length > 0 && (onFavScreen() || document.getElementById("categorySelect").value === "찜")) {
    renderFavAlerts(list, favorites);
  }

  // 찜한 행사가 있을 때만 구분 제목 표시
  function addSectionTitle(text) {
    var h = document.createElement("div");
    h.className = "list-section-title";
    h.textContent = text;
    list.appendChild(h);
  }
  if (favorites.length > 0) {
    // 왼쪽 제목 + 오른쪽 [날짜순 | 이름순]
    var favTitle = document.createElement("div");
    favTitle.className = "list-section-title fav-section-title";
    favTitle.innerHTML =
      '<span class="fav-section-label">' + escapeHtml(I18n.t("list.favSection", { n: favorites.length })) + "</span>" + favSortGroupHtml(favSort);
    favTitle.querySelectorAll(".fav-sort-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        changeFavSort(button.dataset.sort);
      });
    });
    list.appendChild(favTitle);
  }

  sorted.forEach(function (item, index) {
    if (favorites.length > 0 && index === favorites.length) {
      addSectionTitle(I18n.t("list.allSection"));
    }
    var div = document.createElement("div");

    var dateText = cardDateText(item);

    var imgSrc = cardImageUrl(item.event); // 사진이 없으면(인스타 행사 등) null → 기본 그림 (P6)
    div.className = "event-item";
    div.dataset.eventId = item.event.id;
    var infoText = hasOriginalInfo(item.event)
      ? extractSummaryInfo(item.event.info, item.event.fee) // 사이드바엔 요금/장소/시간만
      : cardFallbackInfo(item.event); // info 원문이 없으면(인스타 · 수원문화DB) 장소 · 시간 칸
    var isFavorited = favoriteEventIds.has(item.event.id); // 찜 여부 확인

    div.innerHTML =
      '<span class="row-pick" aria-hidden="true">‹</span>' + // PC: 고른 줄 왼쪽 벽돌색 ‹ (11/13 Q4)
      (imgSrc
        ? '<img class="event-thumb" src="' + escapeHtml(imgSrc) + '" loading="lazy">'
        : defaultPosterHtml(item.event, "event-thumb")) +
      (isFavorited ? '<span class="favorite-badge">★</span>' : "") + // 찜한 행사만 별 표시
      favButtonHtml(isFavorited) + // 모바일: 사진 위 ♥ 버튼
      '<div class="event-body">' +
      '<div class="date">' +
      escapeHtml(dateText) +
      "</div>" +
      placeLineHtml(item.event, "place") + // 모바일 목록 줄 (P3): 장소 · 구 (PC 는 CSS 로 숨김)
      pcBadgesHtml(item.event, "row-pc-badges") + // PC 목록 줄 (11/13 Q4 시안 PcSplitD): 배지 → 제목 → 기간 (모바일은 CSS 로 숨김)
      '<div class="title">' +
      escapeHtml(item.event.title) +
      "</div>" +
      '<div class="row-pc-period">' + escapeHtml(pcPeriodText(item)) + "</div>" +
      badgeRowHtml(item.event) + // 모바일: 상태 · D-day (CSS order 로 제목 위에 놓임)
      '<div class="info">' +
      escapeHtml(infoText) +
      "</div>" +
      "</div>";
    posterOnError(div.querySelector("img.event-thumb"), item.event); // 사진을 못 불러오면 기본 그림 (P6)

    div.addEventListener("click", function () {
      // PC: 가운데 상세 칸에 그 행사 (11/13 Q4 D안 — 지도는 핀이 화면 밖일 때만 옮김)
      if (!mobileQuery.matches) {
        pcSelect(item.event.id, { announce: true, pan: true });
        return;
      }
      map.setCenter(
        new kakao.maps.LatLng(item.event.latitude, item.event.longitude),
      );
      map.setLevel(5);
      openDetailModal(item.event);
    });

    // ♥ 버튼은 상세창을 열지 않고 찜만 토글
    div.querySelector(".card-fav").addEventListener("click", function (e) {
      e.stopPropagation();
      toggleFavorite(item.event.id);
    });

    list.appendChild(div);
  });

  // ===== (모바일) 결과 개수 + 미리보기 카드도 같은 목록으로 갱신 =====
  document.getElementById("resultCount").innerHTML =
    I18n.t(recent ? "list.countRecent" : onFavScreen() ? "list.countFav" : "list.countEvents", { n: "<strong>" + sorted.length + "</strong>" }); // P3 시안 "행사 32개"
  updateSheetHeaderLabel();
  lastSortedItems = sorted;
  if (!mobileQuery.matches) {
    pcSyncList(focusRowId); // PC: 지도 아래 카드 줄 대신 가운데 상세 칸 (11/13 Q4 D안)
    return;
  }
  renderPreviewStrip(sorted);
  if (previewEventId !== null) {
    var previewCard = findPreviewCard(previewEventId);
    if (previewCard) {
      scrollStripTo(previewCard); // 보던 카드 위치 유지
      selectMarker(previewEventId, false); // 마커는 새로 그려졌으니 강조도 다시
    } else {
      hidePreview(); // 필터로 사라진 행사면 닫기
    }
  }
  if (previewEventId === null) showDefaultPreview(); // 카드 줄은 늘 떠 있게 (PC · 모바일 지도 탭)
}

// 검색칸 조합 상태 (한글 IME): 조합 중 입력은 치는 중 규칙으로, 조합이 끝나면(스페이스 · 다른 칸 · 엔터) 그대로 규칙으로 다시 거름
//  setComposing = 조합 상태를 적을 곳, rerender = 조합이 끝난 뒤 다시 거를 함수
//  같은 키로 바로 다음 글자 조합이 시작되면(광 → 광ㅂ) 다시 거르지 않음 (다음 input 이 거름)
function watchSearchComposition(input, setComposing, rerender) {
  var composing = false;
  input.addEventListener("compositionstart", function () {
    composing = true;
    setComposing(true);
  });
  input.addEventListener("compositionend", function () {
    composing = false;
    setComposing(false);
    setTimeout(function () {
      if (!composing) rerender();
    }, 0);
  });
}
function setMapSearchComposing(on) {
  mapSearchComposing = on;
}

document.getElementById("searchInput").addEventListener("input", function (e) {
  mapSearchComposing = !!e.isComposing;
  filterMarkers();
});
watchSearchComposition(document.getElementById("searchInput"), setMapSearchComposing, filterMarkers);
document
  .getElementById("categorySelect")
  .addEventListener("change", filterMarkers);
document
  .getElementById("dateFilter")
  .addEventListener("change", filterMarkers);

// ===== 로그인 상태 + 내 즐겨찾기 목록 확인 =====
var isLoggedIn = false;
var favoriteEventIds = new Set();
// 비회원 찜: 이 기기 브라우저 저장소 (guest-favorites.js). 로그인하면 계정에 합치고 비움
var guestFavorites = createGuestFavoriteStore(
  (function () {
    try {
      return window.localStorage;
    } catch (e) {
      return null;
    }
  })(),
  "suwonbible.guestFavorites",
);

// 로그인 직후: 이 기기 찜을 계정 찜에 합침. 실패하면 기기 목록을 그대로 둠 → 다음 로그인 확인 때 다시
function mergeGuestFavorites() {
  var ids = guestFavorites.list();
  if (!ids.length) return Promise.resolve();
  return fetch("/api/favorites/merge", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ eventIds: ids }),
  })
    .then(function (response) {
      if (!response.ok) throw new Error("HTTP " + response.status);
      return response.json();
    })
    .then(function (result) {
      guestFavorites.clear();
      var text = guestMergeToastText(result.added);
      if (text) showToast(text, I18n.t("fav.view"), showFavoriteList);
    })
    .catch(function (err) {
      console.error("비회원 찜 합치기 실패", err);
    });
}

// 상단 바 · ☰ 메뉴에 보이는 회원 이름 — 이름 글자는 여기 한 곳에서만 정함
// P5(#239): 닉네임이 있으면 닉네임, 없으면(같은 순간 이름 충돌로 이번 응답에 빠진 경우) 회원 번호
function memberLabel(data) {
  return data.nickname || I18n.t("member.number", { n: data.memberId });
}

function checkAuth() {
  fetch("/api/auth/me")
    .then((r) => r.json())
    .then(function (data) {
      isLoggedIn = data.loggedIn;

      var status = document.getElementById("authStatus");
      var loginPageBtn = document.getElementById("loginPageBtn");
      var mypageBtn = document.getElementById("mypageBtn");
      var logoutBtn = document.getElementById("logoutBtn");

      // P4: 비로그인 = [로그인] 만 / 로그인 = 이름 + [마이페이지] (로그아웃은 ☰ 메뉴 줄)
      status.hidden = !isLoggedIn;
      logoutBtn.hidden = !isLoggedIn;
      if (isLoggedIn) {
        status.textContent = memberLabel(data);
        loginPageBtn.style.display = "none";
        mypageBtn.style.display = "inline-block";
        // 로그인 상태면 이 기기 찜을 먼저 합친 뒤 즐겨찾기 목록을 받아옴
        return mergeGuestFavorites().then(function () {
          return fetch("/api/favorites").then((r) => r.json());
        });
      } else {
        status.textContent = "";
        loginPageBtn.style.display = "inline-block";
        mypageBtn.style.display = "none";
        return guestFavorites.list().map(function (id) {
          return { id: id }; // 비회원 찜 (이 기기)
        });
      }
    })
    .then(function (favorites) {
      favoriteEventIds = new Set(
        favorites.map(function (e) {
          return e.id;
        }),
      );
      pcFavoritesReady = true; // PC 첫 진입 중심 = 찜 첫 행사 (B1)
      filterMarkers(); // ← 즐겨찾기 목록 다 받아온 뒤, 화면도 한 번 더 새로 그림
    })
    .catch(function (err) {
      // 로그인 확인 · 찜 목록 받기 실패(배포 중 502 등): 첫 진입 중심 맞추기는 끝냄 — 안 그러면 분류 · 탭을 바꿀 때마다 지도가 첫 행사로 옮겨짐 (#262 검증 ⚠️)
      console.error("로그인 · 찜 목록 확인 실패", err);
      pcFavoritesReady = true;
      if (eventsLoaded) pcAutoCenter = false;
    });
}

document
  .getElementById("logoutBtn")
  .addEventListener("click", function () {
    fetch("/api/auth/logout", { method: "POST" }).then(function () {
      // 로그아웃 줄은 곧 숨겨짐 → 메뉴를 닫고 초점을 ☰ 로 (초점이 사라지지 않게)
      setMenuOpen(false);
      document.getElementById("mobileMenuBtn").focus();
      checkAuth();
    });
  });

checkAuth();

// ===== 사이드바 접기/펼치기 토글 =====
var sidebarToggleBtn = document.getElementById("sidebarToggleBtn");
var sidebarEl = document.getElementById("sidebar");
// 모바일(1024px 이하)은 사이드바가 바텀시트로 바뀌어서 접을 필요 없음 → 처음엔 펼침
var sidebarCollapsed = false;
sidebarEl.classList.toggle("collapsed", sidebarCollapsed);
sidebarToggleBtn.classList.toggle("collapsed", sidebarCollapsed);
sidebarToggleBtn.textContent = sidebarCollapsed ? "‹" : "›";

sidebarToggleBtn.addEventListener("click", function () {
  sidebarCollapsed = !sidebarCollapsed;
  sidebarEl.classList.toggle("collapsed", sidebarCollapsed);
  sidebarToggleBtn.classList.toggle("collapsed", sidebarCollapsed);
  sidebarToggleBtn.textContent = sidebarCollapsed ? "‹" : "›";

  // ===== 카카오맵은 컨테이너 크기가 바뀌어도 스스로 다시 안 그림 =====
  // 사이드바 너비 변하는 애니메이션(0.25s)이 끝난 뒤 지도 크기를 다시 계산시킴
  setTimeout(function () {
    if (map) {
      map.relayout();
    }
    // 하단 카드 줄 폭도 바뀜 → 보던 카드를 다시 가운데로
    var previewCard = previewEventId === null ? null : findPreviewCard(previewEventId);
    if (previewCard) scrollStripTo(previewCard);
  }, 300);
});

// 아이폰 · 아이패드 (아이패드는 PC 사파리처럼 "Macintosh" 로 보고하므로 터치 여부로 구분)
var isAppleMobile =
  /iPhone|iPad|iPod/.test(navigator.userAgent) ||
  (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

// 상세 사진 칸: 사진이 없거나 못 불러오면 기본 그림(P6). 밝은 그림 위에선 PC 의 흰 × 가 안 보이므로 no-image 로 색을 바꿈
function showModalImage(visible, event) {
  document.getElementById("modalImage").hidden = !visible;
  var poster = document.getElementById("modalPoster");
  poster.innerHTML = visible ? "" : defaultPosterHtml(event, "modal-image");
  poster.hidden = visible;
  document.querySelector("#detailModal .modal-card").classList.toggle("no-image", !visible);
}
document.getElementById("modalImage").addEventListener("error", function () {
  if (this.getAttribute("src") && detailEvent) showModalImage(false, detailEvent); // src="" 로 비울 때 나는 error 는 무시
});

// ===== 상세정보 큰 팝업창 열기/닫기 =====
// pager = 모바일 카드 줄에서 열었을 때 앞뒤 행사 { ids, index, scope } (P3, stripDetailPager). 그 밖에서 열면 없음
function openDetailModal(event, pager) {
  // PC 는 상세 창(덮개)을 열지 않고 가운데 상세 칸에 보임 (11/13 Q4 D안 — 공유 링크 ?event= · 홈 · 검색 결과도 같은 길)
  if (!mobileQuery.matches) {
    pcSelect(event.id, { announce: true, pan: true, outside: true });
    return;
  }
  closeRoutePop(false); // PC 길찾기 작은 창이 상세 뒤에 남아 Esc 가 그 창부터 닫지 않게 (#241 검증)
  detailPager = pager || null;
  // 사진이 없으면(인스타 행사) 기본 그림. 불러오다 실패해도 기본 그림 (위 error 처리)
  document.getElementById("modalImage").src = event.imageUrl || "";
  showModalImage(!!event.imageUrl, event);
  document.getElementById("modalTitle").textContent = event.title;
  document.getElementById("modalBadges").innerHTML = badgeRowHtml(event);
  // 날짜 필터 중이면 카드와 같이 "기간 중 · 일정 확인" 을 붙임 (상세만 보고 오늘 한다고 믿지 않게)
  var runDays = filterDays(document.getElementById("dateFilter").value);
  document.getElementById("modalDate").textContent = cardDateText({
    event: event,
    runMatch: runDays ? matchRunDays(event, runDays) : null,
  });
  var scheduleEl = document.getElementById("modalSchedule");
  var scheduleText = formatScheduleDetail(event);
  scheduleEl.textContent = scheduleText;
  scheduleEl.hidden = !scheduleText; // 인라인 display 는 모바일 CSS(.modal-schedule 숨김)를 덮어써서 hidden 속성으로
  document.getElementById("modalInfo").textContent = detailInfoText(event); // info 가 없으면 장소 · 시간 · 출처

  // ===== 원문 링크 버튼: link 없으면 숨김 =====
  var linkBtn = document.getElementById("modalLinkBtn");
  var link = safeLink(event.link);
  if (link) {
    linkBtn.href = link;
    linkBtn.textContent = linkButtonText(event); // 인스타 행사는 "인스타그램에서 보기"
    linkBtn.style.display = "inline-flex";
  } else {
    linkBtn.style.display = "none";
  }

  // ===== 캘린더 저장 버튼: 날짜 없는 행사는 일정을 만들 수 없어서 숨김 =====
  // 📅 캘린더: 아이폰 · 아이패드 = .ics (기본 캘린더 "캘린더에 추가" 화면)
  //           그 외(PC · 안드로이드) = 구글 캘린더 "일정 추가" 화면 (.ics 는 파일로만 받아져서)
  var calBtn = document.getElementById("modalCalBtn");
  var icsAltLink = document.getElementById("modalIcsAltLink");
  var calDateWrap = document.getElementById("modalCalDateWrap");
  var calDateSelect = document.getElementById("modalCalDate");
  // 진행 날짜가 정해진 여러 날 행사 · 30일 넘는 긴 행사는 고른 날 1회 일정 (아니면 지금처럼 기간 전체 종일)
  var choices = event.startDate ? calendarDateChoices(event) : null;
  calDateSelect.innerHTML = "";
  if (choices) {
    choices.dates.forEach(function (date, i) {
      var option = document.createElement("option");
      option.value = date;
      option.textContent = choices.labels[i];
      calDateSelect.appendChild(option);
    });
    calDateSelect.value = choices.selected;
  }
  // 하나뿐이어도 보여 줌 (그 날 하루로 저장된다는 걸 알 수 있게), 고를 게 없으니 잠금
  calDateWrap.hidden = !choices;
  calDateSelect.disabled = !!choices && choices.dates.length < 2;
  function updateCalendarLinks() {
    var query = choices ? "?date=" + encodeURIComponent(calDateSelect.value) : "";
    var icsUrl = "/api/events/" + event.id + "/calendar.ics" + query;
    var googleUrl = "/api/events/" + event.id + "/calendar/google" + query;
    if (isAppleMobile) {
      calBtn.href = icsUrl;
      calBtn.removeAttribute("target");
    } else {
      calBtn.href = googleUrl;
      calBtn.target = "_blank"; // 지도 화면은 그대로 두고 새 탭에서 구글 캘린더
      calBtn.rel = "noopener";
    }
    icsAltLink.href = icsUrl;
  }
  calDateSelect.onchange = updateCalendarLinks;
  if (event.startDate) {
    updateCalendarLinks();
    calBtn.style.display = "inline-flex";
  } else {
    calBtn.style.display = "none";
  }
  icsAltLink.hidden = !event.startDate || isAppleMobile;

  updateFavoriteButton(event.id);

  document.getElementById("modalFavoriteBtn").onclick = function () {
    toggleFavorite(event.id);
  };

  renderDetailMobile(event); // 모바일 전용 부분 (분류 · 구, 정보 표, 소개, 위치, 하단 바)
  renderDetailPager();

  // 모바일은 페이지 전체가 스크롤됨 → 닫을 때 돌아갈 목록 위치를 기억하고 맨 위에서 시작
  if (!document.body.classList.contains("detail-open")) detailReturnScrollY = window.scrollY;
  document.getElementById("detailModal").style.display = "flex";
  document.body.classList.add("detail-open");
  refreshToastPlace(); // 줄 위로 올라가 있던 토스트가 상세 카드를 덮지 않게 기본 자리로
  var card = document.querySelector("#detailModal .modal-card");
  card.scrollTop = 0;
  if (mobileQuery.matches) window.scrollTo(0, 0);
  selectDetailTab("detailRows", false);
  renderDetailMap(event); // 지도는 창이 보인 뒤에 그려야 크기를 잡음
  loadDetailTransit(event); // 주변 탭: 가까운 정류장 · 역 (모바일만)
  measureIntro();
  dialogFocus.opened("detail", card);
  nav.open("detail");
  syncUrlState();
  rememberRecentView(event.id); // 찜 탭 "최근 본" (목록 순서는 다음에 그릴 때 바뀜 — 보던 자리가 밀리지 않게)
}

// ===== 상세 (모바일 디자인 04): 분류 · 구 → 정보 표 → 소개 → 위치 =====
var detailEvent = null;
var detailReturnScrollY = 0; // 상세를 열기 전 페이지 스크롤 위치 (목록 전체에서 열었다 닫으면 그 자리로)

// info 원문을 "라벨: 값" 으로 나눠 시간 · 요금 줄을 찾음 (라벨 이름이 API 마다 달라 포함 여부로)
function infoLines(infoText) {
  var lines = [];
  (infoText || "").split("\n").forEach(function (line) {
    var idx = line.indexOf(":");
    if (idx === -1) return;
    var label = line.substring(0, idx).trim();
    var value = line.substring(idx + 1).trim();
    if (label && value) lines.push({ label: label, value: value });
  });
  return lines;
}

function findInfo(lines, keywords, excludes) {
  var found = lines.filter(function (line) {
    var excluded = (excludes || []).some(function (k) {
      return line.label.indexOf(k) !== -1;
    });
    return !excluded && keywords.some(function (k) {
      return line.label.indexOf(k) !== -1;
    });
  });
  return found.length ? found.map(function (l) { return l.value; }).join(" · ") : null;
}

function renderDetailMobile(event) {
  detailEvent = event;
  var lines = infoLines(event.info);
  var district = eventDistrict(event);
  document.getElementById("detailMeta").textContent =
    [event.category && I18n.name("cat", event.category), district].filter(Boolean).join(" · ");

  // 정보 표: 기간 · 일정 · 시간 · 장소 · 요금 · 문의 · 주최 (값이 있는 줄만)
  // 요금 · 문의 · 주최는 서버가 분해한 칸(V7), 동기화 전 옛 행이면 원문에서 라벨 포함 여부로 찾음
  var fields = detailFields(event);
  var rows = [];
  if (event.startDate) rows.push([I18n.t("detail.period"), document.getElementById("modalDate").textContent]);
  var time = findInfo(lines, ["시간", "요일"]) || fallbackTime(event); // info 가 없으면 timeText
  var schedule = scheduleRowText(event, time); // 일정 원문이 시간 줄과 같으면 빈 값
  if (schedule) rows.push([I18n.t("detail.schedule"), schedule]); // 값 안에 "일정 원문:" 이 있어 라벨은 "진행"
  if (time) rows.push([I18n.t("detail.time"), time]);
  var place = eventPlace(event);
  if (place) rows.push([I18n.t("detail.place"), place]);
  var fee = fields.fee || findInfo(lines, ["이용료", "요금", "입장료", "관람료", "참가비"], ["주차"]); // "주차요금" 은 제외
  if (fee) rows.push([I18n.t("detail.fee"), fee]);
  var contact = fields.contact || findInfo(lines, ["문의"]);
  if (contact) rows.push([I18n.t("detail.contact"), contact]);
  var host = fields.host || findInfo(lines, ["주최", "주관"]);
  if (host) rows.push([I18n.t("detail.host"), host]);
  var source = sourceLabel(event); // 인스타 "인스타그램 @계정" · 수원문화DB
  if (source) rows.push([I18n.t("detail.source"), source]);
  var dl = document.getElementById("detailRows");
  dl.innerHTML = rows
    .map(function (row) {
      return '<div class="detail-row"><dt>' + escapeHtml(row[0]) + "</dt><dd>" + escapeHtml(row[1]) + "</dd></div>";
    })
    .join("");

  // 소개: 원문 전체, 3줄 넘으면 더보기
  var intro = document.getElementById("detailIntroText");
  var more = document.getElementById("detailIntroMore");
  intro.textContent = event.info || "";
  intro.classList.remove("expanded");
  more.setAttribute("aria-expanded", "false");
  more.textContent = I18n.t("detail.more");
  more.hidden = true; // 창이 보인 뒤 measureIntro 가 3줄을 넘을 때만 다시 켬
  document.getElementById("detailIntro").hidden = !event.info;

  // 위치: 주소 (없으면 장소명), 복사 버튼은 복사할 글이 있을 때만
  var address = extractAddress(event.info) || place || "";
  document.getElementById("detailAddress").textContent = address || I18n.t("detail.noLocation");
  document.getElementById("detailCopyBtn").hidden = !address;
}

// 작은 지도 (카카오 StaticMap). 창이 표시된 뒤 호출해야 크기가 잡힘
function renderDetailMap(event) {
  var box = document.getElementById("modalMap");
  box.innerHTML = "";
  if (!mobileQuery.matches || !event.latitude || !event.longitude) return;
  if (!window.kakao || !kakao.maps || !kakao.maps.StaticMap) return;
  var position = new kakao.maps.LatLng(event.latitude, event.longitude);
  new kakao.maps.StaticMap(box, { center: position, level: 4, marker: { position: position } });
}

// 오시는 길: 가까운 정류장(버스 번호) · 역 (transit-nearby.js 가 HTML 을 만듦)
// - 모바일 주변 탭에만 (PC 는 위치 칸이 숨겨져 있어 부르지 않음 → 경기도 API 하루 한도 아낌)
// - 응답이 비었거나 실패면 칸을 숨긴 채 둠 (주소 · 길찾기는 그대로)
var transitTicket = 0; // 응답이 오기 전에 다른 행사를 열면 늦게 온 응답은 버림
function loadDetailTransit(event) {
  var box = document.getElementById("detailTransit");
  var ticket = ++transitTicket;
  box.hidden = true;
  box.innerHTML = "";
  if (!mobileQuery.matches || !event.latitude || !event.longitude) return;
  fetch("/api/events/" + event.id + "/transit")
    .then(function (res) {
      return res.ok ? res.json() : null;
    })
    .then(function (data) {
      if (ticket !== transitTicket || !detailEvent || detailEvent.id !== event.id) return;
      var html = transitNearbyHtml(data);
      if (!html) return;
      box.innerHTML = html;
      box.hidden = false;
    })
    .catch(function () {});
}
document.getElementById("detailTransit").addEventListener("click", function (e) {
  var more = e.target.closest(".transit-more");
  if (more) expandTransitRoutes(more);
});

// 소개가 3줄을 넘을 때만 더보기 표시 (창이 보인 뒤에 재야 함)
function measureIntro() {
  var intro = document.getElementById("detailIntroText");
  document.getElementById("detailIntroMore").hidden = intro.scrollHeight <= intro.clientHeight + 1;
}

// 고정 탭: 정보 → 표 위치로, 주변 → 위치 칸으로 스크롤
function selectDetailTab(targetId, scroll) {
  document.querySelectorAll("#detailTabs .detail-tab").forEach(function (tab) {
    var active = tab.dataset.target === targetId;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", active ? "true" : "false");
  });
  if (!scroll) return;
  var scroller = detailScroller();
  scroller.scrollTo({ top: Math.max(0, sectionScrollTop(scroller, document.getElementById(targetId))), behavior: "smooth" });
}
// 상세가 스크롤되는 곳: 모바일 = 페이지 전체 (사파리 주소창이 줄어들게), PC = 팝업 카드 안
function detailScroller() {
  return mobileQuery.matches ? document.scrollingElement : document.querySelector("#detailModal .modal-card");
}
// 칸(정보 표 · 위치)이 붙어 있는 상단 + 탭 바로 아래(scroll-margin-top 만큼 띄움)에 오는 스크롤 위치
function sectionScrollTop(scroller, section) {
  var margin = parseFloat(getComputedStyle(section).scrollMarginTop) || 0;
  var base = scroller === document.scrollingElement ? 0 : scroller.getBoundingClientRect().top;
  return section.getBoundingClientRect().top - base + scroller.scrollTop - margin;
}
document.querySelectorAll("#detailTabs .detail-tab").forEach(function (tab) {
  tab.addEventListener("click", function () {
    selectDetailTab(tab.dataset.target, true);
  });
});
// 스크롤하면 보이는 칸에 맞춰 탭 표시 (탭은 모바일에만 있음 → 페이지 스크롤)
window.addEventListener("scroll", function () {
  if (!mobileQuery.matches || !document.body.classList.contains("detail-open")) return;
  if (document.getElementById("detailLocation").hidden) return;
  var scroller = document.scrollingElement;
  var atLocation = sectionScrollTop(scroller, document.getElementById("detailLocation")) <= scroller.scrollTop + 2;
  selectDetailTab(atLocation ? "detailLocation" : "detailRows", false);
});

document.getElementById("detailIntroMore").addEventListener("click", function () {
  var intro = document.getElementById("detailIntroText");
  var expanded = intro.classList.toggle("expanded");
  this.setAttribute("aria-expanded", expanded ? "true" : "false");
  this.textContent = I18n.t(expanded ? "detail.less" : "detail.more");
});
document.getElementById("detailBackBtn").addEventListener("click", closeDetailModal);
document.getElementById("detailRouteBtn").addEventListener("click", function () {
  if (detailEvent) openRouteSheet(detailEvent);
});
document.getElementById("detailFavBtn").addEventListener("click", function () {
  if (detailEvent) toggleFavorite(detailEvent.id);
});
document.getElementById("detailHeadFav").addEventListener("click", function () {
  if (detailEvent) toggleFavorite(detailEvent.id);
});
document.getElementById("detailCopyBtn").addEventListener("click", function () {
  var text = document.getElementById("detailAddress").textContent;
  copyText(text).then(
    function () {
      showToast(I18n.t("toast.addrCopied"));
    },
    function () {
      showToast(I18n.t("toast.copyFailedLong"));
    },
  );
});
// ----- 공유 창 (하단 시트): 카카오톡으로 보내기 · 링크 복사. 링크 = 이 행사로 바로 열리는 주소 (?event=번호) -----
var KAKAO_JS_KEY = "7e3b2a42df77bc183241db2bcc6c3cbb"; // 지도 SDK 와 같은 JavaScript 키 (공개용 키)
function eventShareUrl(event) {
  return location.origin + location.pathname + "?event=" + event.id;
}
function openShareSheet(event) {
  document.getElementById("shareEvent").textContent = event.title;
  document.getElementById("shareSheet").hidden = false;
  dialogFocus.opened("share", document.querySelector("#shareSheet .route-sheet"));
  nav.open("share");
}
function closeShareSheet() {
  document.getElementById("shareSheet").hidden = true;
  dialogFocus.closed("share");
  nav.close("share");
}
document.getElementById("detailShareBtn").addEventListener("click", function () {
  if (detailEvent) openShareSheet(detailEvent);
});
document.getElementById("shareCloseBtn").addEventListener("click", closeShareSheet);
document.getElementById("shareCancelBtn").addEventListener("click", closeShareSheet);
document.getElementById("shareSheet").addEventListener("click", function (e) {
  if (e.target.id === "shareSheet") closeShareSheet(); // 덮개를 누르면 닫힘
});
document.getElementById("shareCopyBtn").addEventListener("click", function () {
  if (!detailEvent) return;
  copyText(eventShareUrl(detailEvent)).then(
    function () {
      closeShareSheet();
      showToast(I18n.t("toast.linkCopied"));
    },
    function () {
      showToast(I18n.t("toast.copyFailed"));
    },
  );
});
// 카카오톡 공유: 피드(사진 + 제목 + 설명), 사진이 없으면 서비스 공유 이미지. SDK 를 못 불러왔거나(차단 · 오프라인)
// 콘솔에 이 도메인이 등록되지 않았으면 안내 토스트만 (링크 복사는 그대로 됨)
document.getElementById("shareKakaoBtn").addEventListener("click", function () {
  if (!detailEvent) return;
  // SDK v2 는 Kakao.init() 을 한 뒤에야 Kakao.Share 가 생김 → init 먼저, 그다음 Share 확인
  try {
    if (window.Kakao && !Kakao.isInitialized()) Kakao.init(KAKAO_JS_KEY);
  } catch (err) {
    console.error("카카오 SDK 초기화 실패", err);
  }
  if (!window.Kakao || !Kakao.Share) {
    showToast(I18n.t("share.sdkFailed"));
    return;
  }
  var event = detailEvent;
  var url = eventShareUrl(event);
  var link = { mobileWebUrl: url, webUrl: url };
  var place = eventPlace(event);
  var description = [document.getElementById("modalDate").textContent, place].filter(Boolean).join(" · ");
  try {
    // 행사 사진이 없으면 서비스 공유 이미지(로고 + 수원바이블 · 수원 문화지도)로 → 늘 같은 피드 모양
    Kakao.Share.sendDefault({
      objectType: "feed",
      content: {
        title: event.title,
        description: description,
        imageUrl: new URL(event.imageUrl || "/brand/og-image.png", location.href).href, // 상대 주소여도 절대 주소로
        link: link,
      },
      buttons: [{ title: I18n.t("share.viewOnMap"), link: link }],
    });
    // 카카오톡으로 넘어가는 중 → 기록 칸은 바로 되돌리지 않음 (go(-1) 이 그 이동을 끊을 수 있음). 돌아오면 맞춤
    nav.forget("share");
    closeShareSheet();
  } catch (err) {
    console.error("카카오톡 공유 실패", err);
    showToast(I18n.t("share.failed"));
  }
});

// ----- 상세 좌우 넘기기 (P3 확정 시안 HomeOverscroll ③): 모바일 지도 탭 카드 줄에서 연 상세 -----
// 상세 위 ‹ "상세 · 진행 중 2 / 9" › + 좌우로 밀기 → 카드 줄 앞뒤 행사. 닫으면 카드 줄도 그 행사에 있음
var detailPager = null;

// 지금 카드 줄 순서 그대로 (eventId 의 자리부터)
function stripDetailPager(eventId) {
  var ids = Array.from(document.getElementById("previewStrip").children).map(function (card) {
    return Number(card.dataset.eventId);
  });
  var index = ids.indexOf(eventId);
  if (index < 0 || ids.length < 2) return null;
  return {
    ids: ids,
    index: index,
    scope: listScopeName(document.getElementById("dateFilter").value, currentStatusTab()), // strip-nav.js
  };
}

function renderDetailPager() {
  var box = document.getElementById("detailPager");
  box.hidden = !detailPager;
  if (!detailPager) return;
  document.getElementById("detailPagerText").textContent = detailPagerLabel(
    detailPager.scope,
    detailPager.index,
    detailPager.ids.length,
  ); // strip-nav.js
  var arrows = stripArrowState(detailPager.index, detailPager.ids.length); // strip-nav.js — 처음 · 끝이면 그쪽을 막음
  document.getElementById("detailPrev").disabled = !arrows.prev;
  document.getElementById("detailNext").disabled = !arrows.next;
}

function stepDetail(delta) {
  if (!detailPager) return;
  var target = stripStepTarget(detailPager.index, delta, detailPager.ids.length); // strip-nav.js
  if (target < 0 || target === detailPager.index) return;
  var id = detailPager.ids[target];
  var found = markerList.find(function (item) {
    return item.event.id === id;
  });
  if (!found) return;
  var focused = document.activeElement;
  openDetailModal(found.event, { ids: detailPager.ids, index: target, scope: detailPager.scope });
  // 카드 줄도 같은 행사로 (상세를 닫으면 그 카드 · 마커)
  var card = findPreviewCard(id);
  if (card) {
    previewEventId = id;
    updatePreviewLabel();
    scrollStripTo(card);
    selectMarker(id, true);
  }
  // 넘기기 버튼으로 넘겼으면 초점을 그 버튼에 (끝에 닿아 막히면 반대쪽)
  var prev = document.getElementById("detailPrev");
  var next = document.getElementById("detailNext");
  if (focused === prev || focused === next) {
    if (!focused.disabled) focused.focus();
    else (focused === prev ? next : prev).focus();
  }
}

document.getElementById("detailPrev").addEventListener("click", function () {
  stepDetail(-1);
});
document.getElementById("detailNext").addEventListener("click", function () {
  stepDetail(1);
});

// 좌우로 밀기 (작은 지도 · 정류장 칸 · 입력칸에서 시작한 밀기는 빼고)
(function setupDetailSwipe() {
  var card = document.querySelector("#detailModal .modal-card");
  var start = null;
  card.addEventListener(
    "touchstart",
    function (e) {
      var skip = e.target.closest && e.target.closest("#modalMap, #detailTransit, select, input, textarea");
      start = detailPager && e.touches.length === 1 && !skip ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
    },
    { passive: true },
  );
  card.addEventListener(
    "touchend",
    function (e) {
      if (!start) return;
      var t = e.changedTouches[0];
      var step = swipeStep(t.clientX - start.x, t.clientY - start.y); // strip-nav.js
      start = null;
      if (step) stepDetail(step);
    },
    { passive: true },
  );
  card.addEventListener(
    "touchcancel",
    function () {
      start = null;
    },
    { passive: true },
  );
})();

// ===== 행사 번호로 상세 열기 (홈 카드 · 검색 결과 등) =====
function openDetailFromMarker(eventId) {
  var found = markerList.find(function (item) {
    return item.event.id === eventId;
  });
  if (found) {
    openDetailModal(found.event);
  }
}

function closeDetailModal() {
  var wasOpen = document.body.classList.contains("detail-open");
  document.getElementById("detailModal").style.display = "none";
  document.body.classList.remove("detail-open");
  refreshToastPlace();
  if (wasOpen && mobileQuery.matches) window.scrollTo(0, detailReturnScrollY); // 보던 목록 위치로
  if (wasOpen && homeOpen) drawNearbyMap(); // 상세가 덮은 동안 화면이 돌았으면 작은 지도 크기 다시 맞춤
  detailEvent = null;
  detailPager = null;
  closeShareSheet();
  dialogFocus.closed("detail"); // 보던 목록 위치로 스크롤한 뒤 (포커스가 화면을 끌고 가지 않게)
  nav.close("detail");
  syncUrlState();
}

// ===== ESC 키로 길찾기 · 공유 창 · 상세 모달 닫기 =====
document.addEventListener("keydown", function (e) {
  if (e.key === "Escape") {
    // PC 길찾기 작은 창이 열려 있으면 그것만 닫음
    if (routePopEvent) {
      closeRoutePop(true);
      return;
    }
    // 길찾기 · 공유 창이 상세 위에 열려 있으면 그것만 닫음
    if (!document.getElementById("routeSheet").hidden) {
      closeRouteSheet();
      return;
    }
    if (!document.getElementById("shareSheet").hidden) {
      closeShareSheet();
      return;
    }
    closeDetailModal();
  }
});

// ===== 즐겨찾기 버튼 상태/토글 =====
function updateFavoriteButton(eventId) {
  updatePcFav(); // PC 상세 칸 [♡ 찜] (11/13 Q4)
  // 상세창 위쪽 '★ 찜한 행사' 표시
  document.getElementById("modalFavBadge").hidden = !favoriteEventIds.has(eventId);
  var btn = document.getElementById("modalFavoriteBtn");
  var favorited = favoriteEventIds.has(eventId);
  if (favorited) {
    btn.textContent = I18n.t("detail.favOn");
    btn.classList.add("active");
  } else {
    btn.textContent = I18n.t("detail.favOff");
    btn.classList.remove("active");
  }
  // 모바일 상세: 상단 · 하단 ♡ (채운 하트 + accent)
  ["detailHeadFav", "detailFavBtn"].forEach(function (id) {
    var heart = document.getElementById(id);
    heart.classList.toggle("active", favorited);
    heart.setAttribute("aria-pressed", favorited ? "true" : "false");
    heart.setAttribute("aria-label", I18n.t(favorited ? "fav.unfav" : "fav.fav"));
    heart.innerHTML = favorited
      ? '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" /></svg>'
      : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" /></svg>';
  });
}

function toggleFavorite(eventId) {
  if (!isLoggedIn) {
    // 비회원: 이 기기에 담고 빼기 (서버로 안 보냄, 로그인하면 계정에 합침)
    var added = guestFavorites.toggle(eventId);
    var guestToast = guestFavoriteToast(added);
    if (added === null) {
      showToast(guestToast.text, guestToast.actionLabel, goToLogin); // 가득 참 → 로그인 안내
      return;
    }
    favoriteEventIds = new Set(guestFavorites.list());
    updateFavoriteButton(eventId);
    filterMarkers();
    if (!mobileQuery.matches) pcFavToast(added);
    else showToast(guestToast.text, guestToast.actionLabel, showFavoriteList);
    return;
  }

  if (pendingFavoriteIds.has(eventId)) return; // 요청 중에 또 누르면 무시 (같은 요청이 두 번 가서 "이미 즐겨찾기" 오류가 나지 않게)
  pendingFavoriteIds.add(eventId);

  var isFavorited = favoriteEventIds.has(eventId);
  var method = isFavorited ? "DELETE" : "POST";

  fetch("/api/favorites/" + eventId, { method: method })
    .then(function (response) {
      if (!response.ok) {
        return response.text().then(function (text) {
          if (isLoginRequiredError(text)) {
            // 세션 만료: 화면은 로그인 상태로 남아 있음 → 비로그인으로 바꾸고 로그인 버튼 토스트
            isLoggedIn = false;
            throw new Error("login");
          }
          throw new Error(favoriteErrorText(text));
        });
      }
      if (isFavorited) {
        favoriteEventIds.delete(eventId);
      } else {
        favoriteEventIds.add(eventId);
      }
      updateFavoriteButton(eventId);
      filterMarkers(); // ← 여기로 옮김 (진짜로 상태 바뀐 다음에 다시 그림)
      if (!mobileQuery.matches) {
        pcFavToast(!isFavorited);
        return;
      }
      var toast = favoriteToast(!isFavorited);
      showToast(toast.text, toast.actionLabel, showFavoriteList);
    })
    .catch(function (err) {
      if (!isLoggedIn) {
        showToast(FAVORITE_LOGIN_TOAST.text, FAVORITE_LOGIN_TOAST.actionLabel, goToLogin);
        checkAuth(); // 상단 로그인 표시 · ♥ 표시도 비로그인 기준으로 다시 그림
        return;
      }
      // 네트워크 끊김이면 fetch 가 "Failed to fetch" 같은 영어 글을 던짐 → 기본 문구로
      showToast(err instanceof TypeError ? favoriteErrorText("") : err.message);
    })
    .finally(function () {
      pendingFavoriteIds.delete(eventId);
    });
}
var pendingFavoriteIds = new Set(); // 찜 요청이 아직 안 끝난 행사

function goToLogin() {
  location.href = "login.html";
}

// 토스트 "보기" → 찜 목록 (상세창이 열려 있으면 닫고)
function showFavoriteList() {
  if (detailEvent) closeDetailModal();
  closeSearch(); // 검색 결과에서 찜 → 토스트 "보기" 면 찜 탭이 보이게
  if (mobileQuery.matches) {
    favView = "fav"; // "보기" 는 찜 목록 (최근 본을 보던 중이어도)
    selectTab("fav");
    return;
  }
  if (sidebarCollapsed) sidebarToggleBtn.click(); // PC: 사이드바를 접어 뒀으면 펼쳐서 찜 목록이 보이게
  document.getElementById("categorySelect").value = "찜";
  filterMarkers();
}

// 빈 찜 탭 "행사 둘러보기" → 전체 행사 지도
function browseAllEvents() {
  if (mobileQuery.matches) {
    var select = document.getElementById("categorySelect");
    if (select.value === "찜") select.value = "all"; // '♥ 찜' 칩이 켜진 채면 둘러볼 행사가 없음
    selectTab("map");
    return;
  }
  document.getElementById("categorySelect").value = "all";
  filterMarkers();
}

document
  .getElementById("modalCloseBtn")
  .addEventListener("click", closeDetailModal);
document
  .getElementById("detailModal")
  .addEventListener("click", function (e) {
    if (e.target.id === "detailModal") {
      closeDetailModal(); // 배경 클릭하면 닫힘
    }
  });

// =====================================================
// 모바일 레이아웃 (에어비앤비 스타일)
// - 화면 폭 1024px 이하에서만 동작 (PC 화면은 기존 그대로)
// - 카테고리 칩 / 바텀시트 목록 / 마커 미리보기 카드 / ☰ 메뉴 / 하단 탭
// =====================================================

// ----- 공통 도우미 -----
function favButtonHtml(isFavorited) {
  return (
    '<button type="button" class="card-fav' +
    (isFavorited ? " active" : "") +
    '" aria-label="' + I18n.t("fav.label") + '">' +
    (isFavorited ? "♥" : "♡") +
    "</button>"
  );
}

// ----- 칩 줄 (지도 상단: 분류 드롭다운 칩 · 날짜 해제 · 무료 — PC · 모바일 공통) -----
function chipLabel(value) {
  if (value === "all") return I18n.t("chip.allCategories");
  if (value === "찜") return I18n.t("chip.fav");
  var style = CATEGORY_STYLE[value];
  var name = I18n.name("cat", value); // 값은 원래 분류 이름, 글자만 화면 언어
  return style ? style.emoji + " " + name : name;
}

function renderCategoryChips() {
  var select = document.getElementById("categorySelect");
  var chips = document.getElementById("categoryChips");
  chips.innerHTML = "";

  // 분류는 칩 하나(드롭다운): 칩 모양 글자 위에 투명한 <select> 를 덮어 누르면 휴대폰 기본 선택 창
  // (분류 칩 7개를 늘어놓으면 오른쪽으로 한참 넘겨야 해서 한눈에 안 보임)
  var categoryChip = document.createElement("label");
  categoryChip.id = "categoryChip";
  categoryChip.className = "chip chip-select";
  var categoryText = document.createElement("span");
  categoryText.id = "categoryChipText";
  var categoryArrow = document.createElement("span");
  categoryArrow.className = "chip-arrow";
  categoryArrow.setAttribute("aria-hidden", "true");
  categoryArrow.textContent = "▾";
  var categoryPicker = document.createElement("select");
  categoryPicker.id = "categoryChipSelect";
  categoryPicker.setAttribute("aria-label", I18n.t("chip.category"));
  // 드롭다운(PC) 옵션과 같은 순서 → 고르면 PC 드롭다운 값을 바꾸고 필터 실행
  Array.from(select.options).forEach(function (option) {
    var item = document.createElement("option");
    item.value = option.value;
    item.textContent = chipLabel(option.value);
    categoryPicker.appendChild(item);
  });
  categoryPicker.addEventListener("change", function () {
    select.value = categoryPicker.value;
    filterMarkers();
  });
  categoryChip.append(categoryText, categoryArrow, categoryPicker);
  chips.appendChild(categoryChip);
  // PC 는 위 칩(브라우저 기본 선택 창) 대신 선 아이콘 칩 + 펼침 목록 (R5 A4, CSS 로 하나만 보임)
  chips.appendChild(buildPcCategoryMenu());

  // 날짜 칩(진행 중 · 이번 주말 · 7일 안)은 없앰 (11/11 캘린더 시안): 날짜는 목록 위 날짜 줄 · 달력(모바일),
  // 목록 위 탭(PC)에서 고름. 날짜 필터가 켜져 있으면 분류 칩 바로 뒤에 해제 칩만 (updateDateChip)

  // '무료' 칩: 분류 칩 바로 뒤 (분류 칩과 함께 쓸 수 있음)
  var freeChip = document.createElement("button");
  freeChip.type = "button";
  freeChip.className = "chip";
  freeChip.dataset.value = "__free";
  freeChip.textContent = I18n.t("chip.free");
  freeChip.addEventListener("click", function () {
    freeOnly = !freeOnly;
    filterMarkers();
  });
  chips.appendChild(freeChip);

  // '지난 행사' 칩은 없앰 (11/13 P2): 지난 행사는 달력에서 지난 날짜를 골라 그날 열린 행사로 봄

  updateChipActive();
}

function updateChipActive() {
  var selected = document.getElementById("categorySelect").value;
  document
    .querySelectorAll("#categoryChips .chip")
    .forEach(function (chip) {
      var active = chip.dataset.value === "__free"
          ? freeOnly
          : chip.id === "categoryChip"
            ? selected !== "all" // 분류를 골랐을 때만 켜진 모양
            : chip.dataset.value === selected;
      chip.classList.toggle("active", active);
    });
  // 분류 칩: 고른 값 · 글자를 PC 드롭다운 값에 맞춤 (찜 보기 · 구 지도 등 다른 곳에서 값을 바꿔도)
  var categoryPicker = document.getElementById("categoryChipSelect");
  if (categoryPicker) {
    categoryPicker.value = selected;
    document.getElementById("categoryChipText").textContent = chipLabel(selected);
  }
  updatePcCategoryChip(selected);
  updateDistrictChip();
  updateDateChip();
}

// ----- PC 분류 칩 + 펼침 목록 (11/13 확정 시안 R5 A4 — docs/design/2026-11-13-pc-pending.md "② 검색 · 분류") -----
// 칩 [아이콘 칸 · 이름 · ▾] → 펼침 목록 listbox (선 아이콘 + 분류 색 바탕 + 개수). 고른 값은 숨긴 #categorySelect 에
// 순서 · 개수 · 키보드 판정은 pc-search.js, 색은 theme.css --cat-<분류>-soft · ink · text (지도 핀 CATEGORY_STYLE 과 같은 계열)
var CAT_SLUG = { all: "all", 행사: "event", 공연: "show", 전시: "exhibit", 축제: "festival", 기타: "etc", 찜: "fav" };
var CAT_ICON_PATHS = {
  all: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>', // 네모 4칸
  event: '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>', // 달력
  show: '<path d="M9 18V5.5l11-2V16"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>', // 음표
  exhibit: '<rect x="3.5" y="4.5" width="17" height="15" rx="1.5"/><path d="M6.5 16.5l4-5 3 3.5 2-2 2.5 3.5"/><circle cx="15.5" cy="9" r="1.3"/>', // 액자(산 그림)
  festival: '<path d="M5.5 21V3.5"/><path d="M5.5 4.5h12l-2.5 4 2.5 4h-12"/>', // 깃발
  etc: '<circle cx="5.5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="18.5" cy="12" r="1.4"/>', // 점 3개
  fav: '<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>', // 하트
};
var catMenuOpen = false;
var catActiveIndex = 0; // 펼침 목록에서 키보드로 가리킨 줄
var lastCategoryCounts = null; // 마지막 필터의 분류별 행사 수 (pc-search.js pcCategoryCounts)

function catIconSvg(value, size) {
  return (
    '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size + '" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    CAT_ICON_PATHS[CAT_SLUG[value] || "etc"] +
    "</svg>"
  );
}

function catMenuLabel(value) {
  if (value === "all") return I18n.t("chip.allCategories");
  if (value === "찜") return I18n.t("chip.favOnly");
  return I18n.name("cat", value); // 값은 원래 분류 이름, 글자만 화면 언어
}

// 펼침 목록 줄 = #categorySelect 에 있는 값만 (행사 데이터에 있는 분류), 순서는 시안
function catMenuRows() {
  return pcCategoryRows(
    Array.from(document.getElementById("categorySelect").options).map(function (option) {
      return option.value;
    }),
  );
}

function buildPcCategoryMenu() {
  var wrap = document.createElement("div");
  wrap.id = "catMenu";
  wrap.className = "cat-menu";
  wrap.innerHTML =
    '<button type="button" id="catChipBtn" class="cat-chip" aria-haspopup="listbox" aria-expanded="false" aria-controls="catListbox">' +
    '<span class="cat-ico" aria-hidden="true"></span><span class="cat-chip-name"></span><span class="cat-caret" aria-hidden="true">▾</span>' +
    "</button>" +
    '<div id="catListbox" class="cat-pop" role="listbox" tabindex="-1" hidden></div>';
  var button = wrap.querySelector("#catChipBtn");
  var listbox = wrap.querySelector("#catListbox");
  listbox.setAttribute("aria-label", I18n.t("chip.categoryList"));
  button.addEventListener("click", function () {
    if (catMenuOpen) closeCatMenu(true);
    else openCatMenu();
  });
  button.addEventListener("keydown", function (e) {
    if (catMenuOpen) return;
    var r = pcCategoryKey(e.key, false, catActiveIndex, catMenuRows().length);
    if (r.action !== "open") return;
    e.preventDefault(); // Space 로 지도 · 목록이 스크롤되지 않게
    openCatMenu();
  });
  listbox.addEventListener("keydown", function (e) {
    var rows = catMenuRows();
    var r = pcCategoryKey(e.key, true, catActiveIndex, rows.length);
    if (r.action === "none") return;
    if (r.action === "close") {
      if (e.key === "Tab") {
        closeCatMenu(false); // Tab 은 다음 칸으로 그대로 넘어감
        return;
      }
      e.preventDefault();
      e.stopPropagation(); // Esc 가 상세 창 닫기 · 키 툴팁 끄기(document keydown)까지 가지 않게 (#261 검증 ⚠️)
      closeCatMenu(true);
      return;
    }
    e.preventDefault();
    e.stopPropagation(); // PC 키 처리(← → 넘기기 · Space 찜)로 가지 않게
    if (r.action === "move") setCatActive(r.index, true);
    else if (r.action === "pick") pickCategory(rows[r.index]);
  });
  // 줄 누르기 · 마우스를 올리면 그 줄을 가리킴
  listbox.addEventListener("click", function (e) {
    var option = e.target.closest(".cat-opt");
    if (option) pickCategory(option.dataset.value);
  });
  listbox.addEventListener("mousemove", function (e) {
    var option = e.target.closest(".cat-opt");
    if (!option) return;
    var index = catMenuRows().indexOf(option.dataset.value);
    if (index >= 0 && index !== catActiveIndex) setCatActive(index, false);
  });
  return wrap;
}

// 바깥을 누르면 닫음 (포커스가 목록 안에 있었으면 칩으로)
document.addEventListener("pointerdown", function (e) {
  if (!catMenuOpen) return;
  var menu = document.getElementById("catMenu");
  if (menu && !menu.contains(e.target)) closeCatMenu(menu.contains(document.activeElement));
});

function openCatMenu() {
  var rows = catMenuRows();
  catMenuOpen = true;
  catActiveIndex = Math.max(0, rows.indexOf(document.getElementById("categorySelect").value));
  renderCatList();
  var listbox = document.getElementById("catListbox");
  listbox.hidden = false;
  document.getElementById("catChipBtn").setAttribute("aria-expanded", "true");
  listbox.focus({ preventScroll: true });
}

function closeCatMenu(focusChip) {
  if (!catMenuOpen) return;
  catMenuOpen = false;
  var listbox = document.getElementById("catListbox");
  listbox.hidden = true;
  listbox.removeAttribute("aria-activedescendant");
  var button = document.getElementById("catChipBtn");
  button.setAttribute("aria-expanded", "false");
  if (focusChip) button.focus({ preventScroll: true });
}

function pickCategory(value) {
  document.getElementById("categorySelect").value = value;
  closeCatMenu(true);
  filterMarkers();
}

function setCatActive(index, scroll) {
  catActiveIndex = index;
  var listbox = document.getElementById("catListbox");
  var active = null;
  listbox.querySelectorAll(".cat-opt").forEach(function (option, i) {
    var on = i === index;
    option.classList.toggle("is-active", on);
    if (on) active = option;
  });
  if (active) {
    listbox.setAttribute("aria-activedescendant", active.id);
    if (scroll) active.scrollIntoView({ block: "nearest" });
  }
}

// 펼침 목록 줄: [아이콘 칸] 이름 · 개수 · (고른 줄) 체크. 찜한 행사만 앞에 구분선
function renderCatList() {
  var listbox = document.getElementById("catListbox");
  if (!listbox) return;
  var selected = document.getElementById("categorySelect").value;
  var counts = lastCategoryCounts || {};
  listbox.innerHTML = catMenuRows()
    .map(function (value) {
      var slug = CAT_SLUG[value];
      var isSelected = value === selected;
      return (
        (value === "찜" ? '<div class="cat-sep" aria-hidden="true"></div>' : "") +
        '<div class="cat-opt" role="option" id="catOpt-' + slug + '" data-value="' + escapeHtml(value) + '" data-cat="' + slug + '" aria-selected="' + isSelected + '">' +
        '<span class="cat-ico">' + catIconSvg(value, 16) + "</span>" +
        '<span class="cat-opt-name">' + escapeHtml(catMenuLabel(value)) + "</span>" +
        '<span class="cat-count">' + (counts[value] != null ? counts[value] : "") + "</span>" +
        '<span class="cat-check" aria-hidden="true">' +
        (isSelected ? '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>' : "") +
        "</span></div>"
      );
    })
    .join("");
  if (catMenuOpen) setCatActive(catActiveIndex, false);
}

// 칩 모양을 고른 값에 맞춤 (찜 보기 · 구 지도 등 다른 곳에서 값을 바꿔도 — updateChipActive 가 부름)
function updatePcCategoryChip(selected) {
  var button = document.getElementById("catChipBtn");
  if (!button) return;
  var slug = CAT_SLUG[selected] || "all";
  button.dataset.cat = slug;
  button.querySelector(".cat-ico").innerHTML = catIconSvg(selected in CAT_SLUG ? selected : "all", 14);
  button.querySelector(".cat-chip-name").textContent = catMenuLabel(selected in CAT_SLUG ? selected : "all");
}

// 날짜 필터가 켜져 있으면(목록 날짜 줄 · 달력 · 홈 날짜 칩) 분류 칩 바로 뒤에 "📅 11월 14일 (토) ✕" — 누르면 해제
// 날짜를 고르는 칩이 아니라 지도 탭에서도 걸린 필터를 보이고 풀 수 있게 하는 칩. 모바일 전용 (PC 는 목록 위 탭에 보임)
function updateDateChip() {
  var chips = document.getElementById("categoryChips");
  var chip = document.getElementById("dateChip");
  var title = dateFilterTitle(document.getElementById("dateFilter").value);
  if (!title) {
    if (chip) chip.remove();
    return;
  }
  if (!chip) {
    chip = document.createElement("button");
    chip.type = "button";
    chip.id = "dateChip";
    chip.className = "chip chip-mobile-only";
    chip.addEventListener("click", function () {
      setDateFilter("all");
    });
    var categoryChip = document.getElementById("categoryChip");
    chips.insertBefore(chip, categoryChip ? categoryChip.nextSibling : chips.firstChild);
  }
  chip.classList.add("active"); // 위 updateChipActive 루프가 data-value 없는 이 칩의 active 를 지우므로 매번 다시 켬
  chip.textContent = "📅 " + title + " ✕";
  chip.setAttribute("aria-label", I18n.t("chip.clearDate", { title: title }));
}

// ----- 날짜 고르기 (calendar-strip.js, 11/11 시안 CalClosed · CalOpen · PcCard) -----
// 모바일 = 목록 화면 위 탭 줄 [진행 중 N · 진행 예정 N · 달력 ▾] (PC 와 같음, P3 — 2주 날짜 줄은 안 씀)
// PC = 목록 위 탭 [진행 중 N · 진행 예정 N · 달력 ▾]. 날짜를 고르면 #dateFilter 값("2026-11-14") → 목록 · 지도 · 카드 줄 모두 그날만
// PC 탭: null 전체(기본, 진행 중 + 진행 예정 — 11/12 사용자) | "open" 진행 중 | "upcoming" 진행 예정
// 켜진 탭을 다시 누르면 전체로. 날짜를 고르면 탭 대신 날짜
var pcStatusTab = null;
var calOpen = false; // 한 달 달력이 펼쳐져 있나
var calShownMonth = null; // 달력에 보이는 달 { year, month }
var calPickerOpen = false; // "2026년 11월 ▾" 연/월 빠른 고르기가 열려 있나
var calPickerYear = null; // 빠른 고르기에 보이는 해
var lastDateBaseItems = []; // 마지막 필터의 "날짜 빼고 통과한 행사" (달력 넘기기 등 필터 없이 다시 그릴 때)
var lastDateVisibleCount = 0;

// PC 진행 탭은 날짜 · 검색어 · '찜' 이 없을 때만 걸림 (calendar-strip.js statusTabApplies, PC · 모바일 같음)
// 모바일 목록 화면도 같은 탭 줄 [진행 중 N · 진행 예정 N · 달력 ▾] (P3 확정 시안 MobileList) → mobile 값은 넘기지 않음
function currentStatusTab() {
  return statusTabApplies({
    dateValue: document.getElementById("dateFilter").value,
    keyword: document.getElementById("searchInput").value.trim(),
    category: document.getElementById("categorySelect").value,
  })
    ? pcStatusTab
    : null;
}

// 날짜 필터 바꾸기. 날짜 하나("2026-11-14")는 #dateFilter 에 없는 값이라 숨은 옵션 하나를 그 날짜로 맞춰 둠
function setDateFilter(value) {
  var select = document.getElementById("dateFilter");
  var dayOption = select.querySelector("option[data-day]");
  if (isDayValue(value)) {
    if (!dayOption) {
      dayOption = document.createElement("option");
      dayOption.dataset.day = "true";
      select.appendChild(dayOption);
    }
    dayOption.value = value;
    dayOption.textContent = dayTitle(value);
  }
  select.value = value;
  filterMarkers();
}

// 모바일은 목록 탭에서만 (지도 · 찜 · 홈에선 숨김), PC 는 늘
function updateDatePanelVisibility() {
  document.getElementById("datePanel").hidden = onFavScreen() || (mobileQuery.matches && activeTab() !== "list");
}

function renderDatePanel(baseItems, visibleCount, favScreen) {
  lastDateBaseItems = baseItems;
  lastDateVisibleCount = visibleCount;
  updateDatePanelVisibility();
  if (favScreen) return;

  var dateValue = document.getElementById("dateFilter").value;
  var events = baseItems.map(function (item) {
    return item.event;
  });
  var today = koreaToday();
  var marked = highlightedDays(dateValue);
  var dayMode = dateValue !== "all";
  var panel = document.getElementById("datePanel");
  panel.classList.toggle("cal-open", calOpen);
  if (!calOpen && calPickerOpen) setCalPicker(false); // 달력이 닫히면 빠른 고르기도 (PC 날짜 · 탭을 키보드로 누른 경우 등)

  // PC 탭: 날짜를 골랐으면 진행 탭 대신 "달력" 탭이 켜지고 고른 날짜를 보여 줌
  // 검색어 · 찜 중엔 진행 탭이 안 걸리므로 둘 다 꺼진 모양 (누르면 그 탭으로 거름)
  var appliedTab = currentStatusTab();
  document.querySelectorAll("#datePanel .pc-date-tab").forEach(function (tab) {
    var on = tab.dataset.status === appliedTab;
    tab.classList.toggle("active", on);
    tab.setAttribute("aria-pressed", on ? "true" : "false");
  });
  document.getElementById("pcOpenCount").textContent = events.filter(function (event) {
    return matchesStatusTab(event, "open");
  }).length;
  document.getElementById("pcUpcomingCount").textContent = events.filter(function (event) {
    return matchesStatusTab(event, "upcoming");
  }).length;
  var calBtn = document.getElementById("pcCalBtn");
  calBtn.textContent = (dayMode ? dateFilterTitle(dateValue) : I18n.t("cal.calendar")) + (calOpen ? " ▴" : " ▾");
  calBtn.classList.toggle("active", dayMode);
  calBtn.setAttribute("aria-expanded", calOpen ? "true" : "false");
  document.getElementById("calOpenBtn").setAttribute("aria-expanded", calOpen ? "true" : "false");

  // 날짜 줄 (오늘부터 2주)
  var days = stripDays(today, STRIP_DAYS);
  var stripCounts = countRunDays(
    events,
    days.map(function (day) {
      return day.date;
    }),
  );
  // 날짜 줄은 오늘부터라 지난 날을 골랐으면 오늘의 달 (10월 15일을 골라도 줄 위는 11월)
  document.getElementById("stripMonth").textContent = I18n.month(monthOf(marked[0] && marked[0] >= today ? marked[0] : today).month);
  var strip = document.getElementById("dateStrip");
  var stripScroll = strip.scrollLeft;
  rebuildKeepingFocus(strip, function () {
    days.forEach(function (day) {
      var count = stripCounts[day.date];
      var button = dayButton("strip-day", day, marked, count);
      button.innerHTML =
        '<span class="strip-dow">' + day.label + "</span>" +
        '<span class="strip-num">' + day.num + "</span>" +
        '<span class="strip-count">' + (count ? I18n.t("cal.count", { n: count }) : "-") + "</span>";
      strip.appendChild(button);
    });
  });
  strip.scrollLeft = stripScroll;

  // 한 달 달력 (펼쳤을 때만)
  document.getElementById("calMonth").hidden = !calOpen;
  if (calOpen) renderCalendarMonth(events, marked, today);

  // 목록 위 날짜 칸: 하루면 "◂ 전날 · 11월 8일 (일) · 다음날 ▸" + "행사 9개 [전체 날짜]",
  // 주말 · 7일이면 "이번 주말 · 행사 9개 [전체 날짜]". 지난 날이면 아래 안내 (11/13 P2)
  var title = dateFilterTitle(dateValue);
  var day = singleDay(dateValue);
  document.getElementById("dateHeading").hidden = !title;
  document.getElementById("dayNav").hidden = !day;
  if (day) {
    document.getElementById("dayNavLong").textContent = title;
    document.getElementById("dayNavShort").textContent = shortDayTitle(day);
  }
  if (title) document.getElementById("dateHeadingText").textContent = (day ? "" : title + " · ") + I18n.t("cal.events", { n: visibleCount });
  var pastDay = pastSelectedDay(dateValue);
  var note = document.getElementById("pastDayNote");
  note.hidden = !pastDay;
  if (pastDay) note.textContent = pastDayNote(pastDay);
}

function renderCalendarMonth(events, marked, today) {
  if (!calShownMonth) calShownMonth = monthOf(marked[0] || today);
  var cells = monthCells(calShownMonth.year, calShownMonth.month);
  var dates = cells.filter(Boolean);
  var counts = countRunDays(events, dates);
  updateCalTitle();
  // 달 이동 "‹ 10월" "12월 ›" — 지난달도 갈 수 있음 (11/13 P2)
  [["calPrev", -1, I18n.t("cal.prevMonth")], ["calNext", 1, I18n.t("cal.nextMonth")]].forEach(function (nav) {
    var button = document.getElementById(nav[0]);
    button.textContent = monthStepLabel(calShownMonth, nav[1]);
    button.setAttribute("aria-label", nav[2] + " (" + I18n.month(shiftMonth(calShownMonth, nav[1]).month) + ")");
  });
  var grid = document.getElementById("calGrid");
  rebuildKeepingFocus(grid, function () {
    I18n.list("date.weekdays").forEach(function (name, i) {
      var head = document.createElement("span");
      head.className = "cal-dow" + (i === 0 ? " sun" : i === 6 ? " sat" : "");
      head.textContent = name;
      head.setAttribute("aria-hidden", "true");
      grid.appendChild(head);
    });
    cells.forEach(function (date) {
      if (!date) {
        grid.appendChild(document.createElement("span"));
        return;
      }
      var count = counts[date];
      var button = dayButton("cal-day", { date: date, weekday: weekdayOf(date) }, marked, count);
      if (date === today) button.classList.add("today");
      if (isPastDay(date)) button.classList.add("past"); // 지난 날 = 회색 바탕, 눌러서 그날 열린 행사
      button.innerHTML =
        '<span class="cal-num">' + +date.slice(8, 10) + "</span>" +
        '<span class="cal-dots" aria-hidden="true">' + "■".repeat(dotLevel(count)) + "</span>";
      grid.appendChild(button);
    });
  });
}

// 날짜 줄 · 달력 칸 공통: 고른 날 표시 · 토 · 일 색 · 읽기 프로그램용 "11월 14일 (토) 행사 9개"
function dayButton(className, day, marked, count) {
  var button = document.createElement("button");
  button.type = "button";
  var on = marked.indexOf(day.date) !== -1;
  button.className = className + (day.weekday === 0 ? " sun" : day.weekday === 6 ? " sat" : "") + (on ? " active" : "");
  button.dataset.day = day.date;
  button.setAttribute("aria-pressed", on ? "true" : "false");
  button.setAttribute("aria-label", I18n.t("cal.dayLabel", { day: dayTitle(day.date), n: count || 0 }));
  return button;
}

// 칸을 다시 그려도 키보드로 누르던 날짜에 초점이 남게
function rebuildKeepingFocus(container, build) {
  var focused = container.contains(document.activeElement) ? document.activeElement.dataset.day : null;
  container.innerHTML = "";
  build();
  if (focused) {
    var again = container.querySelector('[data-day="' + focused + '"]');
    if (again) again.focus();
  }
}

function refreshDatePanel() {
  renderDatePanel(lastDateBaseItems, lastDateVisibleCount, onFavScreen());
}

function openCalendar(open) {
  calOpen = open;
  if (open) calShownMonth = null; // 고른 날짜(없으면 오늘)의 달부터
  setCalPicker(false); // 열 때 · 닫을 때 모두 빠른 고르기는 닫힌 채로 (키보드로 탭 · 날짜를 눌러 달력이 닫힌 경우도)
  refreshDatePanel();
}

// 달력 제목 "2026년 11월 ▾" (빠른 고르기가 열리면 ▴)
function updateCalTitle() {
  if (!calShownMonth) return;
  var titleBtn = document.getElementById("calTitle");
  var ym = I18n.yearMonth(calShownMonth.year, calShownMonth.month);
  titleBtn.textContent = ym + " " + (calPickerOpen ? "▴" : "▾");
  titleBtn.setAttribute("aria-label", I18n.t("cal.pickLabel", { ym: ym }));
  titleBtn.setAttribute("aria-expanded", calPickerOpen ? "true" : "false");
}

// ----- 연/월 빠른 고르기 (11/13 P2): "2026년 11월 ▾" 바로 아래 붙어 달력을 덮음. 위 ‹ 2025 · 2026년 · 2027 ›, 아래 1~12월 4×3
// 여닫기만으로는 달력 칸을 다시 그리지 않음 (바깥 날짜를 눌러 닫을 때 누른 칸이 사라져 첫 클릭이 씹히지 않게 — 검증 ⚠️)
function setCalPicker(open) {
  calPickerOpen = open;
  document.getElementById("calPicker").hidden = !open;
  if (open) {
    calPickerYear = calShownMonth.year;
    renderCalPicker();
  }
  updateCalTitle();
}

function renderCalPicker() {
  document.getElementById("calPickerYear").textContent = I18n.year(calPickerYear);
  var prevYear = document.getElementById("calPickerPrevYear");
  var nextYear = document.getElementById("calPickerNextYear");
  prevYear.textContent = "‹ " + (calPickerYear - 1);
  prevYear.setAttribute("aria-label", I18n.year(calPickerYear - 1));
  nextYear.textContent = calPickerYear + 1 + " ›";
  nextYear.setAttribute("aria-label", I18n.year(calPickerYear + 1));
  var months = document.getElementById("calPickerMonths");
  months.innerHTML = "";
  for (var month = 1; month <= 12; month++) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "cal-picker-month";
    button.dataset.month = month;
    button.textContent = I18n.month(month);
    var current = calPickerYear === calShownMonth.year && month === calShownMonth.month;
    if (current) {
      button.classList.add("active"); // 지금 달력에 보이는 달
      button.setAttribute("aria-current", "true");
    }
    button.setAttribute("aria-label", I18n.yearMonth(calPickerYear, month));
    months.appendChild(button);
  }
}

function closeCalPicker(focusTitle) {
  if (!calPickerOpen) return;
  setCalPicker(false);
  if (focusTitle) document.getElementById("calTitle").focus();
}

document.getElementById("calTitle").addEventListener("click", function () {
  if (calPickerOpen) {
    closeCalPicker(false);
    return;
  }
  setCalPicker(true);
  var current = document.querySelector("#calPickerMonths .active") || document.querySelector("#calPickerMonths button");
  current.focus();
});
[["calPickerPrevYear", -1], ["calPickerNextYear", 1]].forEach(function (pair) {
  document.getElementById(pair[0]).addEventListener("click", function () {
    calPickerYear += pair[1];
    renderCalPicker();
  });
});
document.getElementById("calPickerMonths").addEventListener("click", function (e) {
  var button = e.target.closest("button[data-month]");
  if (!button) return;
  calShownMonth = { year: calPickerYear, month: +button.dataset.month };
  closeCalPicker(true); // 닫고 그 달 달력으로 다시 그림
  refreshDatePanel();
});
// Esc = 빠른 고르기만 닫음 (달력까지 접지 않게 calMonth 의 Esc 로 안 넘김)
document.getElementById("calPicker").addEventListener("keydown", function (e) {
  if (e.key !== "Escape") return;
  e.preventDefault();
  e.stopPropagation();
  closeCalPicker(true);
});
// 바깥을 누르면 닫힘 (제목 버튼은 위 click 에서 여닫음)
document.addEventListener(
  "pointerdown",
  function (e) {
    if (!calPickerOpen) return;
    if (document.getElementById("calPicker").contains(e.target) || document.getElementById("calTitle").contains(e.target)) return;
    closeCalPicker(false);
  },
  true,
);

// ----- 하루 이동 ◂ 전날 · 다음날 ▸ (11/13 P2, 날짜를 하루 골랐을 때 목록 위 날짜 칸)
["dayPrevBtn", "dayNextBtn"].forEach(function (id, i) {
  document.getElementById(id).addEventListener("click", function () {
    var day = singleDay(document.getElementById("dateFilter").value);
    if (!day) return;
    var next = addDays(day, i === 0 ? -1 : 1);
    if (calOpen) calShownMonth = monthOf(next); // 펼친 달력(모바일)도 그 날짜의 달로
    setDateFilter(next);
  });
});

function onDayClick(e) {
  var button = e.target.closest("button[data-day]");
  if (!button || button.disabled) return;
  var closesCalendar = calOpen && !mobileQuery.matches; // PC 달력은 고르면 닫힘 (모바일은 펼친 채 아래 목록이 바뀜)
  if (closesCalendar) calOpen = false;
  setDateFilter(nextDayFilter(document.getElementById("dateFilter").value, button.dataset.day));
  if (closesCalendar) document.getElementById("pcCalBtn").focus(); // 누른 칸이 숨겨져도 초점은 달력 버튼으로
}
// 달력에서 Esc = 접기, 초점은 펼친 버튼으로 (PC 달력 ▾ · 모바일 달력 펼치기)
document.getElementById("calMonth").addEventListener("keydown", function (e) {
  if (e.key !== "Escape") return;
  e.preventDefault();
  e.stopPropagation(); // 페이지 전체 Esc(상세 닫기 등)로 넘기지 않음
  openCalendar(false);
  document.getElementById("pcCalBtn").focus(); // 모바일도 탭 줄의 달력 ▾ (P3)
});
document.getElementById("dateStrip").addEventListener("click", onDayClick);
document.getElementById("calGrid").addEventListener("click", onDayClick);
document.getElementById("calOpenBtn").addEventListener("click", function () {
  openCalendar(true);
  document.getElementById("calCloseBtn").focus();
});
document.getElementById("calCloseBtn").addEventListener("click", function () {
  openCalendar(false);
  document.getElementById("calOpenBtn").focus();
});
document.getElementById("pcCalBtn").addEventListener("click", function () {
  openCalendar(!calOpen);
});
["calPrev", "calNext"].forEach(function (id, i) {
  document.getElementById(id).addEventListener("click", function () {
    calShownMonth = shiftMonth(calShownMonth, i === 0 ? -1 : 1);
    if (calPickerOpen) setCalPicker(false);
    refreshDatePanel();
  });
});
document.querySelectorAll("#datePanel .pc-date-tab").forEach(function (tab) {
  tab.addEventListener("click", function () {
    // 켜진 탭을 다시 누르면 해제 → 전체 (날짜를 골라 둔 상태에서 누르면 그 탭으로)
    pcStatusTab = currentStatusTab() === tab.dataset.status ? null : tab.dataset.status;
    calOpen = false;
    setDateFilter("all");
  });
});
document.getElementById("dateClearBtn").addEventListener("click", function () {
  setDateFilter("all");
});
// PC ↔ 모바일: 펼친 달력은 접고 목록을 다시 그림
mobileQuery.addEventListener("change", function () {
  calOpen = false;
  if (calPickerOpen) setCalPicker(false);
  filterMarkers();
});

// 구 필터가 켜져 있으면 칩 줄 맨 앞에 "📍 팔달구 ✕" — 누르면 구 필터 해제
function updateDistrictChip() {
  var chips = document.getElementById("categoryChips");
  var chip = document.getElementById("districtChip");
  if (!districtFilter) {
    if (chip) chip.remove();
    return;
  }
  if (!chip) {
    chip = document.createElement("button");
    chip.type = "button";
    chip.id = "districtChip";
    chip.className = "chip";
    chip.addEventListener("click", function () {
      setDistrictFilter(null);
    });
    chips.insertBefore(chip, chips.firstChild);
    chips.scrollLeft = 0; // 가로 스크롤 뒤에 숨지 않게
  }
  chip.classList.add("active"); // 위 updateChipActive 루프가 data-value 없는 이 칩의 active 를 지우므로 매번 다시 켬
  var districtName = I18n.name("district", districtFilter);
  chip.textContent = "📍 " + districtName + " ✕";
  chip.setAttribute("aria-label", I18n.t("chip.clearFilter", { name: districtName }));
}

// ----- 바텀시트 (사이드바 목록을 아래에서 끌어올리는 방식) -----
// 상태별 시트 윗변 위치(px)를 화면 크기에 맞춰 계산
function getSheetPositions() {
  var appHeight = document.getElementById("app").clientHeight;
  var headerHeight = document.getElementById("sheetHeader").offsetHeight;
  return {
    full: 0, // 목록 보기 = 지도 · 검색창까지 다 덮는 화면 (P3 확정 시안 MobileList, 머리줄 "행사 N개 · 지도 보기")
    peek: appHeight - headerHeight, // 결과 바만 보이게 (아이폰 홈 바 여백은 하단 탭이 맡음)
  };
}

function setSheetY(y, animate) {
  var sidebar = document.getElementById("sidebar");
  sheetY = y;
  sidebar.style.transition = animate ? "" : "none";
  sidebar.style.transform = "translateY(" + y + "px)";
}

// 목록 전체 = 페이지 전체가 스크롤 (index.css, 사파리 주소창이 줄어들게)
// 켜고 끌 때 보던 목록 위치를 목록 상자 ↔ 페이지 사이로 옮김 (끌어내리는 동안에도 위치 유지)
function setListPageScroll(on) {
  if (document.body.classList.contains("sheet-full") === on) return;
  var list = document.getElementById("list");
  var top = on ? list.scrollTop : window.scrollY;
  document.body.classList.toggle("sheet-full", on);
  if (on) {
    window.scrollTo(0, top);
  } else {
    window.scrollTo(0, 0);
    list.scrollTop = top;
  }
}

function setSheetState(state) {
  if (!mobileQuery.matches) return;
  sheetState = state;
  // 배치가 바뀌므로 클래스를 먼저 바꾼 뒤 위치 계산
  setListPageScroll(state === "full");
  document.body.classList.toggle("sheet-peek", state === "peek");
  var y = getSheetPositions()[state];
  setSheetY(y, true);
  // 시트가 내려가 있는 만큼 목록 아래 여백 → 마지막 카드까지 스크롤 가능 (전체는 CSS 가 하단 탭 자리만큼)
  document.getElementById("list").style.paddingBottom = state === "full" ? "" : y + 24 + "px";
  document.getElementById("sheetToggleText").textContent = I18n.t(state === "peek" ? "sheet.list" : "sheet.map");
  updateSheetHeaderLabel();
  if (state !== "peek") hidePreview(); // 목록이 지도를 덮으면 하단 카드는 닫음
  else if (previewEventId === null) showDefaultPreview(); // 지도 탭 = 카드 줄 기본 표시 (P3)
  updateTabBar();
}

// 지도 탭이 보이나 (모바일): 홈 · 목록 · 찜 · 검색 화면이 지도를 덮고 있지 않음 → 카드 줄 기본 표시 (strip-nav.js stripDefaultShown)
function mobileMapShown() {
  return !homeOpen && !favTabOpen && !searchOpen && sheetState === "peek";
}

// 결과 바(버튼)의 보조기기용 이름 · 열림 상태
function updateSheetHeaderLabel() {
  var header = document.getElementById("sheetHeader");
  var expanded = sheetState !== "peek";
  header.setAttribute("aria-expanded", expanded ? "true" : "false");
  header.setAttribute(
    "aria-label",
    document.getElementById("resultCount").textContent + ", " + I18n.t(expanded ? "sheet.map" : "sheet.list"),
  );
}

function nearestSheetState(y) {
  var pos = getSheetPositions();
  return ["full", "peek"].reduce(function (best, s) {
    return Math.abs(pos[s] - y) < Math.abs(pos[best] - y) ? s : best;
  }, "peek");
}

// 결과 바를 끌어서 올리고 내리기. 탭 · Enter · Space = 지도 탭 ↔ 목록 보기 (P3: 절반 단계 없음)
(function setupSheetDrag() {
  var header = document.getElementById("sheetHeader");
  var ORDER = ["full", "peek"];
  var dragging = false;
  var startY = 0;
  var startSheetY = 0;
  var lastY = 0;
  var lastTime = 0;
  var velocity = 0; // px/ms (+는 아래 방향)

  header.addEventListener("pointerdown", function (e) {
    if (!mobileQuery.matches) return;
    dragging = true;
    startY = lastY = e.clientY;
    lastTime = Date.now();
    startSheetY = sheetY;
    velocity = 0;
    header.setPointerCapture(e.pointerId);
  });

  header.addEventListener("pointermove", function (e) {
    if (!dragging) return;
    // 실제로 끌기 시작하면 페이지 스크롤을 끄고 목록 상자 스크롤로 (페이지를 내린 채 시트만 움직이면 어긋나 보임)
    if (Math.abs(e.clientY - startY) >= 6) setListPageScroll(false);
    var pos = getSheetPositions();
    var y = startSheetY + (e.clientY - startY);
    y = Math.max(pos.full, Math.min(pos.peek, y)); // 위·아래 끝 제한
    var now = Date.now();
    velocity = (e.clientY - lastY) / Math.max(1, now - lastTime);
    lastY = e.clientY;
    lastTime = now;
    setSheetY(y, false); // 손가락을 바로 따라오게 애니메이션 없이
  });

  function endDrag(e) {
    if (!dragging) return;
    dragging = false;

    // 거의 안 움직였으면 '탭'으로 처리
    if (e.type === "pointerup" && Math.abs(e.clientY - startY) < 6) {
      toggleListView();
      return;
    }

    var from = ORDER.indexOf(nearestSheetState(startSheetY));
    var target;
    if (velocity < -0.4) {
      target = ORDER[Math.max(0, from - 1)]; // 위로 튕기면 한 단계 위
    } else if (velocity > 0.4) {
      target = ORDER[Math.min(ORDER.length - 1, from + 1)]; // 아래로 튕기면 한 단계 아래
    } else {
      target = nearestSheetState(sheetY); // 천천히 놓으면 가까운 위치로
    }
    // 탭을 거쳐서 → 찜 화면을 끌어내려도 찜 상태가 남지 않고 지도 탭 · 카드 줄로 (#240 검증 ⚠️)
    selectTab(target === "peek" ? "map" : favTabOpen ? "fav" : "list");
  }
  header.addEventListener("pointerup", endDrag);
  header.addEventListener("pointercancel", endDrag);

  // 키보드: Enter · Space
  header.addEventListener("keydown", function (e) {
    if (!mobileQuery.matches) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      toggleListView();
    }
  });
})();

// "목록 보기 ▴" ↔ "지도 보기": 목록 탭 ↔ 지도 탭과 같음 (찜 화면에서 "지도 보기"도 지도 탭으로)
function toggleListView() {
  selectTab(sheetState === "peek" ? "list" : "map");
}
// 목록 보기 화면 하단 가운데 떠 있는 [지도 보기] (P3 확정 시안 MobileList)
document.getElementById("listMapBtn").addEventListener("click", function () {
  selectTab("map");
});

// ----- 하단 탭 (홈 · 지도 · 목록 · 찜 · 마이) -----
// 한 화면(index.html) 안의 상태를 탭으로 오가게 함: 홈 = 지도 위를 덮는 홈 화면, 지도 = 목록 살짝,
// 목록 = 목록 전체, 찜 = 찜 화면(favTabOpen, 분류 값은 안 바꿈) + 목록 전체
// 마이는 mypage.html 링크 (로그인 안 했으면 그 페이지가 login.html 로 보냄)
function activeTab() {
  if (homeOpen) return "home";
  if (favTabOpen) return "fav";
  return sheetState === "full" ? "list" : "map";
}

function updateTabBar() {
  var current = activeTab();
  document.querySelectorAll("#tabBar .tab-item").forEach(function (tab) {
    var active = tab.dataset.tab === current;
    tab.classList.toggle("active", active);
    if (active) {
      tab.setAttribute("aria-current", "page");
    } else {
      tab.removeAttribute("aria-current");
    }
  });
  updateDatePanelVisibility(); // 날짜 줄은 모바일 목록 탭에서만
}

function selectTab(tab) {
  if (tab === "home") {
    showHome();
    return;
  }
  hideHome();
  hidePreview();
  favTabOpen = tab === "fav"; // 분류 드롭다운은 건드리지 않음 → 공연 칩 → 찜 탭 → 지도 탭이면 공연 그대로
  filterMarkers();
  setSheetState(tab === "map" ? "peek" : "full");
}

document.querySelectorAll("#tabBar button[data-tab]").forEach(function (tab) {
  tab.addEventListener("click", function () {
    selectTab(tab.dataset.tab);
  });
});

// ----- 홈 화면 (11/11 시안 3칸): ① 지금 진행 중 ② 곧 종료 | 곧 시작 (7일 안) ③ 달별 행사 + 내 주변 -----
// 고르는 규칙은 home-picks.js. 페이지 전체가 스크롤 (index.css body.home-open, 사파리 주소창이 줄어들게)
var homeSoonMode = "ending"; // "ending" 곧 종료 | "starting" 곧 시작
var homeMonthKey = null; // 달별 행사에서 고른 달 ("2026-12"). 없으면 첫 달

function showHome() {
  nav.close("tab"); // 홈에서 나가며 쌓은 칸을 되돌림 (뒤로 가기로 왔으면 이미 빠짐)
  favView = "fav"; // 찜 탭을 나감 → 다음엔 '찜'부터
  if (favTabOpen) {
    favTabOpen = false;
    filterMarkers(); // 찜 화면 흔적(탭 줄 · 칩 흐림 · tabpanel · "찜 N개")을 지움 → 홈 뒤 PC 폭으로 바뀌어도 남지 않게
  }
  hidePreview();
  homeOpen = true; // 시트를 내리기 전에 → 지도 탭 카드 줄이 홈 뒤에서 뜨지 않게 (mobileMapShown)
  if (sheetState !== "peek") setSheetState("peek"); // 목록 전체(페이지 스크롤)는 풀어 두고 홈으로
  document.body.classList.add("home-open");
  window.scrollTo(0, 0);
  drawNearbyMap(); // 숨은 칸에선 크기가 0 이라 작은 지도는 보일 때 그림
  updateTabBar();
}

function hideHome() {
  if (!homeOpen) return;
  nav.open("tab"); // 지도 · 목록 · 찜에서 뒤로 가기 → 홈 (탭끼리 옮겨도 이 한 칸)
  homeOpen = false;
  document.body.classList.remove("home-open");
  window.scrollTo(0, 0);
}

function homeEvents() {
  return markerList.map(function (item) {
    return item.event;
  });
}

function renderHome() {
  renderHomeNow();
  renderHomeSoon();
  renderHomeMonths();
  renderHomeNearby();
}

window.addEventListener("resize", function () {
  if (!homeOpen) return;
  drawNearbyMap(); // 화면 회전 · 폭 변화 뒤 작은 지도 크기 · 범위 다시 맞춤
});

// 사진 칸 (사진이 없거나 못 불러오면 기본 그림 P6). 진행 중 · 달별 카드가 같이 씀
function cardPosterHtml(event, badge) {
  var status = badge ? eventStatus(event) : null;
  return (
    '<span class="poster-img">' +
    defaultPosterHtml(event, "dp-fill") + // 사진이 없거나 못 불러오면(사진을 숨김) 보이는 기본 그림 (P6)
    (event.imageUrl ? '<img src="' + escapeHtml(event.imageUrl) + '" alt="" loading="lazy">' : "") +
    (status ? '<span class="status-badge ' + status + '">' + statusLabel(status) + "</span>" : "") +
    "</span>"
  );
}

// 카드를 누르면 상세, 사진 실패 → 숨겨서 아래 기본 그림이 보이게
function bindHomeCards(root) {
  root.querySelectorAll(".home-card").forEach(function (card) {
    var img = card.querySelector("img");
    if (img) hideOnError(img);
    card.addEventListener("click", function () {
      openDetailFromMarker(Number(card.dataset.eventId));
    });
  });
}

// ① 지금 진행 중: 가로 카드 200px (사진 4:3 + 운영중 배지 + 제목 + 종료까지 D-N · 구)
function nowCardHtml(event) {
  var sub = [ddayText(event), eventDistrict(event)].filter(Boolean);
  return (
    '<button type="button" class="home-card now-card" data-event-id="' + event.id + '">' +
    cardPosterHtml(event, true) +
    '<span class="card-title">' + escapeHtml(event.title) + "</span>" +
    '<span class="row-sub">' + escapeHtml(sub.join(" · ")) + "</span>" +
    "</button>"
  );
}

function renderHomeNow() {
  var events = homeEvents();
  var count = nowOpenEvents(events).length;
  var cards = nowOpenCards(events);
  var track = document.getElementById("homeNowTrack");
  track.removeAttribute("aria-busy");
  document.getElementById("homeNowCount").textContent = count;
  document.getElementById("homeNowAll").hidden = count === 0;
  if (cards.length === 0) {
    // 진행 중이 없거나, 진행 중 행사가 모두 아래 "곧 종료"에 나옴 (겹쳐 보이지 않게)
    track.innerHTML =
      '<p class="home-empty">' +
      escapeHtml(I18n.t(count ? "home.nowAllInSoon" : "home.noneNow")) +
      "</p>";
    return;
  }
  // 끝에 좁은 안내 (P3 확정 시안 HomeOverscroll): 밀어서 보이면 "더 밀면 전체", 끝에서 한 번 더 밀면 지도 탭 (아래 끝 넘김)
  // 누르거나 키보드로도 같은 곳으로 (버튼)
  track.innerHTML =
    cards.map(nowCardHtml).join("") +
    '<button type="button" class="now-more" aria-label="' + escapeHtml(I18n.t("home.nowMoreLabel")) + '">' +
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>' +
    '<span class="now-more-text" aria-hidden="true">' + escapeHtml(I18n.t("home.nowMore")) + "</span>" +
    "</button>";
  track.scrollLeft = 0; // 다시 그릴 때 처음 카드부터
  bindHomeCards(track);
  track.querySelector(".now-more").addEventListener("click", function () {
    showNowEvents("map");
  });
}

// 진행 중 행사 전체: 날짜 필터 오늘(값 today = 홈 수 N 과 같은 판정) + 다른 필터는 풀어서 목록 수 = N
//  - "전체 →" = 목록 탭, 끝 넘김 · 끝 안내 = 지도 탭 + 카드 줄 (P3, 거기서 연 상세는 좌우로 앞뒤 행사)
function showNowEvents(tab) {
  document.getElementById("searchInput").value = "";
  document.getElementById("categorySelect").value = "all";
  document.getElementById("dateFilter").value = "today";
  freeOnly = false;
  districtFilter = null;
  pcStatusTab = null;
  selectTab(tab); // filterMarkers 가 목록 · 칩 · 카드 줄까지 맞춤
}
document.getElementById("homeNowAll").addEventListener("click", function () {
  showNowEvents("list");
});

// 끝 넘김: 끝에 닿은 채로 시작해 왼쪽으로 한 번 더 밀면 지도 탭 (home-picks.js homeEndSwipe — 첫 밀기는 안내만 보임)
(function setupNowEndSwipe() {
  var track = document.getElementById("homeNowTrack");
  var start = null;
  track.addEventListener(
    "touchstart",
    function (e) {
      if (e.touches.length !== 1 || !track.querySelector(".now-more")) {
        start = null;
        return;
      }
      start = {
        x: e.touches[0].clientX,
        y: e.touches[0].clientY,
        atEnd: atScrollEnd(track.scrollLeft, track.scrollWidth, track.clientWidth),
      };
    },
    { passive: true },
  );
  track.addEventListener(
    "touchend",
    function (e) {
      if (!start) return;
      var t = e.changedTouches[0];
      var go = homeEndSwipe(start.atEnd, t.clientX - start.x, t.clientY - start.y);
      start = null;
      if (go) showNowEvents("map");
    },
    { passive: true },
  );
  track.addEventListener(
    "touchcancel",
    function () {
      start = null;
    },
    { passive: true },
  );
})();

// 행사 행 (디자인 2번 공통 부품): 포스터 56×70 + 배지 + 제목 + 보조 줄(분류 · 구 · 기간)
// 사진이 없거나 못 불러오면 기본 그림(P6). 홈 곧 종료 · 곧 시작에서 쓰고, 검색 결과(S2)에서도 같이 씀
function eventRowHtml(event) {
  var status = eventStatus(event);
  var dday = ddayText(event);
  var sub = [event.category && I18n.name("cat", event.category), eventDistrict(event), formatEventDates(event)].filter(Boolean);
  return (
    '<button type="button" class="event-row" data-event-id="' + event.id + '">' +
    '<span class="row-poster">' +
    defaultPosterHtml(event, "dp-fill") + // 사진이 없거나 못 불러오면(사진을 숨김) 보이는 기본 그림 (P6)
    (event.imageUrl ? '<img src="' + escapeHtml(event.imageUrl) + '" alt="" loading="lazy">' : "") +
    "</span>" +
    '<span class="row-body">' +
    (status
      ? '<span class="row-badges"><span class="status-badge ' + status + '">' + statusLabel(status) + "</span>" +
        (dday ? '<span class="dday-badge">' + dday + "</span>" : "") +
        "</span>"
      : "") +
    '<span class="row-title">' + escapeHtml(event.title) + "</span>" +
    '<span class="row-sub">' + escapeHtml(sub.join(" · ")) + "</span>" +
    "</span>" +
    "</button>"
  );
}

// 행 안의 사진 실패 → 숨겨서 아래 기본 그림(P6)이 보이게 · 행을 누르면 상세
function bindEventRows(root) {
  root.querySelectorAll(".event-row").forEach(function (row) {
    var img = row.querySelector("img");
    if (img) hideOnError(img);
    row.addEventListener("click", function () {
      openDetailFromMarker(Number(row.dataset.eventId));
    });
  });
}

// ② 곧 종료 | 곧 시작: 탭 이름 옆 수 + 고른 탭의 행 (7일 안 전부)
function renderHomeSoon() {
  var list = document.getElementById("homeSoonList");
  list.removeAttribute("aria-busy");
  var ending = endingSoon(homeEvents());
  var starting = startingSoon(homeEvents());
  document.getElementById("homeEndingCount").textContent = ending.length;
  document.getElementById("homeStartingCount").textContent = starting.length;
  var isEnding = homeSoonMode === "ending";
  var picks = isEnding ? ending : starting;
  if (picks.length === 0) {
    list.innerHTML =
      '<p class="home-empty">' +
      escapeHtml(I18n.t(isEnding ? "home.noneEnding" : "home.noneStarting")) +
      "</p>";
    return;
  }
  list.innerHTML = picks.map(eventRowHtml).join("");
  bindEventRows(list);
}

// ③ 달별 행사: 달 칩 "12월 · 14" + 그 달 카드 110px (사진 정사각 + 제목 + 기간)
function monthCardHtml(event) {
  return (
    '<button type="button" class="home-card month-card" data-event-id="' + event.id + '">' +
    cardPosterHtml(event, false) +
    '<span class="card-title">' + escapeHtml(event.title) + "</span>" +
    '<span class="row-sub">' + escapeHtml(formatEventDates(event) || "") + "</span>" +
    "</button>"
  );
}

var homeMonthList = []; // monthGroups 결과 (달 칩을 누를 때 다시 고르지 않게)

// 행사를 받을 때마다: 달 칩 다시 만들기 (고른 달이 없어졌으면 첫 달)
function renderHomeMonths() {
  homeMonthList = monthGroups(homeEvents());
  var section = document.getElementById("homeMonths");
  section.hidden = homeMonthList.length === 0;
  if (!homeMonthList.length) return;
  var keep = homeMonthList.some(function (group) {
    return group.key === homeMonthKey;
  });
  if (!keep) homeMonthKey = homeMonthList[0].key;
  var chips = document.getElementById("homeMonthChips");
  chips.innerHTML = homeMonthList
    .map(function (group) {
      return (
        '<button type="button" class="month-chip" data-month="' + group.key + '" aria-pressed="false">' +
        group.label + " · " + group.events.length +
        "</button>"
      );
    })
    .join("");
  chips.querySelectorAll(".month-chip").forEach(function (chip) {
    chip.addEventListener("click", function () {
      selectHomeMonth(chip.dataset.month);
    });
  });
  selectHomeMonth(homeMonthKey);
}

// 달 칩 누름: 칩은 다시 그리지 않고 선택 표시만 바꿈 (키보드 포커스가 칩에 남게) + 그 달 카드
function selectHomeMonth(key) {
  homeMonthKey = key;
  document.querySelectorAll("#homeMonthChips .month-chip").forEach(function (chip) {
    var active = chip.dataset.month === key;
    chip.classList.toggle("active", active);
    chip.setAttribute("aria-pressed", active ? "true" : "false");
  });
  var group = homeMonthList.filter(function (g) {
    return g.key === key;
  })[0];
  var track = document.getElementById("homeMonthTrack");
  track.innerHTML = group ? group.events.map(monthCardHtml).join("") : "";
  track.scrollLeft = 0; // 달을 바꾸면 처음 카드부터
  bindHomeCards(track);
}

function renderHomeLoading() {
  document.getElementById("homeNowCount").textContent = "…";
  document.getElementById("homeNowAll").hidden = true;
  document.getElementById("homeEndingCount").textContent = "";
  document.getElementById("homeStartingCount").textContent = "";
  document.getElementById("homeMonths").hidden = true; // 불러오는 동안은 칸을 비워 둠
  document.getElementById("homeNearby").hidden = true;
  var track = document.getElementById("homeNowTrack");
  track.setAttribute("aria-busy", "true");
  var card =
    '<div class="home-card now-card" aria-hidden="true"><span class="poster-img sk"></span>' +
    '<span class="sk sk-line w80"></span><span class="sk sk-line w40"></span></div>';
  track.innerHTML = card + card;
  var list = document.getElementById("homeSoonList");
  list.setAttribute("aria-busy", "true");
  var row =
    '<div class="event-row" aria-hidden="true"><span class="row-poster sk"></span>' +
    '<span class="row-body"><span class="sk sk-line w40"></span><span class="sk sk-line w80"></span>' +
    '<span class="sk sk-line w60"></span></span></div>';
  list.innerHTML = row + row + row;
}

function renderHomeError() {
  document.getElementById("homeNowCount").textContent = "-";
  document.getElementById("homeNowAll").hidden = true;
  var track = document.getElementById("homeNowTrack");
  track.removeAttribute("aria-busy");
  track.innerHTML = "";
  document.getElementById("homeMonths").hidden = true;
  document.getElementById("homeNearby").hidden = true;
  var list = document.getElementById("homeSoonList");
  list.removeAttribute("aria-busy");
  list.innerHTML =
    '<div class="home-empty" role="alert"><span>' + escapeHtml(I18n.t("home.loadFailed")) + "</span>" +
    '<button type="button" id="homeRetryBtn">' + escapeHtml(I18n.t("common.retry")) + "</button></div>";
  document.getElementById("homeRetryBtn").addEventListener("click", loadEvents);
}

// 곧 종료 / 곧 시작 탭 (좌우 화살표 키로도 바꿈)
function selectHomeSoon(mode, focus) {
  homeSoonMode = mode;
  document.querySelectorAll("#homeView .home-tab").forEach(function (tab) {
    var active = tab.dataset.soon === mode;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", active ? "true" : "false");
    tab.tabIndex = active ? 0 : -1;
    if (active) {
      document.getElementById("homeSoonList").setAttribute("aria-labelledby", tab.id);
      if (focus) tab.focus();
    }
  });
  if (eventsLoaded) renderHomeSoon();
}

document.querySelectorAll("#homeView .home-tab").forEach(function (tab) {
  tab.addEventListener("click", function () {
    selectHomeSoon(tab.dataset.soon, false);
  });
  tab.addEventListener("keydown", function (e) {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    e.preventDefault();
    selectHomeSoon(homeSoonMode === "ending" ? "starting" : "ending", true);
  });
});

// ----- 내 주변 (디자인 01): 구 칩 4개 + 작은 지도 + "○○구 행사 N개 · 지도에서 보기" → 지도 탭 + 구 필터 -----
// 구 · 행사 고르기는 home-picks.js (districtEvents). "가까운" 칩은 위치 권한이 필요해 나중 (계획 4번)
var homeDistrict = HOME_DEFAULT_DISTRICT;
var nearbyPicks = []; // 고른 구의 종료 안 된 행사
var nearbyMap = null; // 작은 지도 (홈이 처음 보일 때 만듦 — 숨은 칸에 만들면 크기가 0)
var nearbyMarkers = [];
var NEARBY_DOT_ICON =
  "data:image/svg+xml;charset=UTF-8," +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14">' +
      '<rect x="1" y="1" width="12" height="12" fill="#b4472a" stroke="#fff" stroke-width="2"/></svg>', // --accent
  );

function renderHomeNearby() {
  var chips = document.getElementById("homeDistrictChips");
  if (!chips.children.length) {
    HOME_DISTRICTS.forEach(function (district) {
      var chip = document.createElement("button");
      chip.type = "button";
      chip.className = "chip";
      chip.dataset.district = district;
      chip.textContent = I18n.name("districtShort", district); // 장안 · 권선 · 팔달 · 영통
      chip.addEventListener("click", function () {
        homeDistrict = district;
        renderHomeNearby();
      });
      chips.appendChild(chip);
    });
  }
  chips.querySelectorAll(".chip").forEach(function (chip) {
    var active = chip.dataset.district === homeDistrict;
    chip.classList.toggle("active", active);
    chip.setAttribute("aria-pressed", active ? "true" : "false");
  });

  nearbyPicks = districtEvents(homeEvents(), homeDistrict).filter(function (event) {
    return event.latitude && event.longitude;
  });
  var go = document.getElementById("homeNearbyGo");
  go.disabled = nearbyPicks.length === 0;
  go.textContent = nearbyPicks.length
    ? I18n.t("home.nearbyGo", { name: I18n.name("district", homeDistrict), n: nearbyPicks.length })
    : I18n.t("home.nearbyNone", { name: I18n.name("district", homeDistrict) });
  document.getElementById("homeNearby").hidden = false;
  drawNearbyMap();
}

// 지도를 행사들이 다 보이게 맞춤. 한 곳이면 그 자리 확대, 없으면 수원 전체쯤 (padding = [위, 오른쪽, 아래, 왼쪽] px)
function fitMapToEvents(target, events, padding) {
  if (events.length === 0) {
    target.setLevel(8);
    target.setCenter(new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng));
    return;
  }
  var bounds = new kakao.maps.LatLngBounds();
  events.forEach(function (event) {
    bounds.extend(new kakao.maps.LatLng(event.latitude, event.longitude));
  });
  if (bounds.getSouthWest().equals(bounds.getNorthEast())) {
    target.setLevel(4);
    target.setCenter(bounds.getSouthWest());
    return;
  }
  target.setBounds(bounds, padding[0], padding[1], padding[2], padding[3]);
}

// 작은 지도: 고른 구 행사 자리에 작은 벽돌색 네모. 손으로 움직이지 않고, 누르면 "지도에서 보기"와 같음
function drawNearbyMap() {
  var box = document.getElementById("homeNearbyMap");
  if (!map || !eventsLoaded || !box.offsetWidth) return; // SDK 전 · 행사 받기 전 · 홈이 안 보이면 (PC · 상세 위) 건너뜀
  if (!nearbyMap) {
    nearbyMap = new kakao.maps.Map(box, {
      center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
      level: 7,
      draggable: false,
      scrollwheel: false,
      disableDoubleClick: true,
      disableDoubleClickZoom: true,
      keyboardShortcuts: false,
    });
    kakao.maps.event.addListener(nearbyMap, "click", goNearbyOnMap);
  } else {
    nearbyMap.relayout(); // 화면 회전 등으로 칸 크기가 바뀌었을 수 있음
  }
  nearbyMarkers.forEach(function (marker) {
    marker.setMap(null);
  });
  var image = new kakao.maps.MarkerImage(NEARBY_DOT_ICON, new kakao.maps.Size(14, 14));
  nearbyMarkers = groupByLocation(
    nearbyPicks.map(function (event) {
      return { event: event };
    }),
  ).map(function (group) {
    return new kakao.maps.Marker({
      map: nearbyMap,
      position: new kakao.maps.LatLng(group[0].event.latitude, group[0].event.longitude),
      image: image,
      clickable: false, // 누르면 지도 클릭으로 → goNearbyOnMap
    });
  });
  fitMapToEvents(nearbyMap, nearbyPicks, [20, 20, 20, 20]);
}

// "지도에서 보기": 다른 필터(검색어 · 분류 · 날짜 · 지난 행사)는 풀고 그 구만 → "N개"와 지도 결과 수가 같음
function goNearbyOnMap() {
  showDistrictOnMap(homeDistrict, nearbyPicks);
}

// 구 하나만 지도에: 다른 필터는 풀고 구 필터만 → 지도 결과 수 = events 수. 홈 "내 주변" · 검색 "📍 ○○구" 행이 씀
function showDistrictOnMap(district, events) {
  if (!events.length) return;
  document.getElementById("searchInput").value = "";
  document.getElementById("categorySelect").value = "all";
  document.getElementById("dateFilter").value = "all";
  freeOnly = false;
  districtFilter = district; // selectTab 이 filterMarkers 로 지도 · 목록 · 칩을 다시 그림
  selectTab("map");
  var top = document.getElementById("topbar").offsetHeight + 16; // 검색창 · 칩 줄 아래
  var bottom = document.getElementById("sheetHeader").offsetHeight + 16; // 결과 바 위
  fitMapToEvents(map, events, [top, 40, bottom, 40]);
}

document.getElementById("homeNearbyGo").addEventListener("click", goNearbyOnMap);

// 모바일 ↔ PC 폭이 바뀔 때 (칩 줄은 PC · 모바일 모두 지도 위에 있어 구 · 무료 필터는 그대로 둠)
mobileQuery.addEventListener("change", function (e) {
  if (e.matches) {
    // 모바일로 넘어오면: 지도 탭이면 카드 줄 그대로(글만 모바일로), 홈 · 목록 등이 덮고 있으면 닫음 (P3)
    if (!mobileMapShown()) hidePreview();
    else if (previewEventId !== null) updatePreviewLabel();
    // 모바일에선 지도 검색창을 누르면 검색 화면이 열려 PC 에서 친 검색어를 지울 수 없음 → 넘어올 때 풂
    var searchInput = document.getElementById("searchInput");
    if (searchInput.value) {
      searchInput.value = "";
      filterMarkers();
    }
    return;
  }
  closeSearch(); // 검색 화면은 모바일 전용
  if (previewEventId === null) showDefaultPreview(); // PC 로 넘어오면 카드 줄 바로 (applyLayout 은 크기 변경이 멈춘 뒤라 늦음)
});

// 찜 | 최근 본 탭 줄도 모바일 전용 → 폭이 바뀌면 다시 그림 (PC 로 가면 찜 목록으로)
mobileQuery.addEventListener("change", function (e) {
  if (!e.matches) favView = "fav";
  if (favTabOpen || document.getElementById("categorySelect").value === "찜") filterMarkers();
});

// ----- 검색 화면 (디자인 06 입력 중): 홈 검색 버튼 · 지도 탭 검색창 → 화면 전체 -----
// 고르기 · 정렬 · 강조 자리는 home-picks.js (searchEvents · searchHighlight · searchDistrict)
var searchOpen = false;
var searchViewInput = document.getElementById("searchViewInput");

function openSearch() {
  if (!mobileQuery.matches || searchOpen) return;
  hidePreview();
  searchOpen = true;
  searchMode = "suggest"; // 지난 검색어는 남겨 두고 입력 중 화면부터
  document.getElementById("searchView").hidden = false;
  document.body.classList.add("search-open");
  renderSearch();
  searchViewInput.focus();
  nav.open("search");
}

function closeSearch() {
  if (!searchOpen) return;
  nav.close("search");
  searchOpen = false;
  searchViewInput.blur();
  document.getElementById("searchView").hidden = true;
  document.body.classList.remove("search-open");
  if (previewEventId === null) showDefaultPreview(); // 지도 탭으로 돌아오면 카드 줄 (P3)
}

// 제목 + 검색어 강조 (띄어쓰기 무시한 자리). 장소로만 맞으면 강조 없음. typing = 치는 중 규칙 (home-picks.js searchTyping)
function searchTitleHtml(title, keyword, typing) {
  var range = searchHighlight(title, keyword, typing);
  if (!range) return escapeHtml(title);
  return (
    escapeHtml(title.slice(0, range.start)) +
    '<mark class="search-hit">' + escapeHtml(title.slice(range.start, range.end)) + "</mark>" +
    escapeHtml(title.slice(range.end))
  );
}

// 행: [상태 배지(폭 고정)] 제목(한 줄 말줄임) … 구. 날짜 없는 행사는 배지 자리만 비움
function searchRowHtml(event, keyword, typing) {
  var status = eventStatus(event);
  return (
    '<button type="button" class="search-row" data-event-id="' + event.id + '">' +
    '<span class="status-badge ' + (status || "none") + '">' + (status ? statusLabel(status) : "") + "</span>" +
    '<span class="search-title">' + searchTitleHtml(event.title || "", keyword, typing) + "</span>" +
    '<span class="search-where">' + escapeHtml(eventDistrict(event) || "") + "</span>" +
    "</button>"
  );
}

var searchMode = "suggest"; // "suggest" 입력 중(06) | "results" 엔터 뒤 결과(07)

// 맨 위 "📍 팔달구 · 지도에서 행사 N개" (검색어가 구 이름일 때, N 은 홈 내 주변과 같은 기준). 해당 없으면 ""
function searchDistrictRowHtml(district, count) {
  if (!district || !count) return "";
  return (
    '<button type="button" class="search-row search-district" data-district="' + district + '">' +
    '<span class="search-pin" aria-hidden="true">📍</span>' +
    '<span class="search-title">' + I18n.t("search.districtRow", { name: "<strong>" + escapeHtml(I18n.name("district", district)) + "</strong>", n: count }) + "</span>" +
    "</button>"
  );
}

// 결과 행 (디자인 07): 홈과 같은 행사 행(포스터 56×70 · 배지 · D-day) + 오른쪽 ♡. 버튼 안에 버튼을 못 넣어 형제로 둠
function searchResultRowHtml(event) {
  return (
    '<div class="result-row">' +
    eventRowHtml(event) +
    favButtonHtml(favoriteEventIds.has(event.id)).replace(
      'class="card-fav',
      'data-event-id="' + event.id + '" aria-pressed="' + favoriteEventIds.has(event.id) + '" class="card-fav',
    ) +
    "</div>"
  );
}

// 입력 중 · 결과 화면을 그림. 행사 행 → 상세 (검색 화면은 상세 뒤에 남음 → 닫으면 다시 검색)
function renderSearch() {
  var list = document.getElementById("searchSuggest");
  var keyword = searchViewInput.value;
  var results = searchMode === "results";
  document.getElementById("searchClearBtn").hidden = !keyword;
  if (!eventsLoaded) {
    list.innerHTML = eventsLoadFailed
      ? '<p class="search-empty" role="alert">' + escapeHtml(I18n.t("search.loadFailed")) + "</p>"
      : '<p class="search-empty">' + escapeHtml(I18n.t("search.loading")) + "</p>";
    return;
  }
  if (!keyword.trim()) {
    list.innerHTML = '<p class="search-empty">' + escapeHtml(I18n.t("search.hint")) + "</p>";
    return;
  }
  var events = homeEvents();
  var district = searchDistrict(keyword);
  var districtPicks = district ? districtEvents(events, district) : [];
  // 입력 중: 조합 중이면 마지막 글자를 넓혀 찾음 ("과" → 광복, 그대로 맞는 행사가 위), 조합이 끝났으면 그대로 찾아 없을 때만 ("숭" → 수원). 엔터 결과는 그대로
  var typing = !results && searchTypingMode(events, keyword, searchViewComposing);
  var picks = searchEvents(events, keyword, undefined, typing); // 운영중 · 오픈예정(종료 임박순) → 날짜 없음 → 종료
  var html = searchDistrictRowHtml(district, districtPicks.length);
  if (results) {
    // 결과: 맞는 행사 전부 (입력 중 행과 같은 규칙이라 종료 행사도 맨 아래에 나옴)
    html +=
      '<div class="result-head"><span>' + I18n.t("search.resultCount", { n: "<strong>" + I18n.t("cal.count", { n: picks.length }) + "</strong>" }) + "</span>" +
      '<span class="result-sort">' + escapeHtml(I18n.t("search.sort")) + "</span></div>";
    html += picks.length
      ? picks.map(searchResultRowHtml).join("")
      : '<div class="search-empty" role="status"><strong>' + escapeHtml(I18n.t("search.noResult", { q: keyword.trim() })) + "</strong>" +
        "<span>" + escapeHtml(I18n.t("search.noResultTip")) + "</span></div>";
  } else {
    html += picks
      .slice(0, SEARCH_SUGGEST_MAX)
      .map(function (event) {
        return searchRowHtml(event, keyword, typing);
      })
      .join("");
    if (!html) html = '<p class="search-empty">' + escapeHtml(I18n.t("search.noMatch", { q: keyword.trim() })) + "</p>";
  }
  list.innerHTML = html;
  list.scrollTop = 0;
  list.querySelectorAll(".search-row[data-event-id]").forEach(function (row) {
    row.addEventListener("click", function () {
      openDetailFromMarker(Number(row.dataset.eventId));
    });
  });
  bindEventRows(list); // 결과 행: 사진 실패 → 기본 그림, 누르면 상세
  list.querySelectorAll(".result-row .card-fav").forEach(function (button) {
    button.addEventListener("click", function () {
      toggleFavorite(Number(button.dataset.eventId)); // 끝나면 filterMarkers → updateSearchFavs 가 ♡ 표시를 맞춤
    });
  });
  var districtRow = list.querySelector(".search-district");
  if (districtRow) {
    districtRow.addEventListener("click", function () {
      closeSearch();
      showDistrictOnMap(district, districtPicks);
    });
  }
}

// 결과 화면 ♡ 표시를 찜 목록에 맞춤 (찜 토글 · 로그인 확인 뒤 filterMarkers 가 부름)
function updateSearchFavs() {
  if (!searchOpen) return;
  document.querySelectorAll("#searchSuggest .result-row .card-fav").forEach(function (button) {
    var on = favoriteEventIds.has(Number(button.dataset.eventId));
    button.classList.toggle("active", on);
    button.setAttribute("aria-pressed", on ? "true" : "false"); // 화면 낭독기도 찜 상태를 알게
    button.textContent = on ? "♥" : "♡";
  });
}

// 엔터: 결과 화면(07)으로. 키보드는 내림. 빈 검색어 엔터는 그대로
function showSearchResults() {
  if (!searchViewInput.value.trim()) return;
  searchMode = "results";
  searchViewInput.blur();
  renderSearch();
}

var searchViewComposing = false; // 검색 화면 입력칸에서 마지막 글자를 IME 로 조합하는 중 → 치는 중 규칙
searchViewInput.addEventListener("input", function (e) {
  searchMode = "suggest"; // 다시 입력하면 입력 중 화면으로
  searchViewComposing = !!e.isComposing;
  renderSearch();
});
watchSearchComposition(
  searchViewInput,
  function (on) {
    searchViewComposing = on;
  },
  function () {
    if (searchOpen) renderSearch();
  },
);
searchViewInput.addEventListener("keydown", function (e) {
  if (e.key !== "Enter" || e.isComposing) return; // 한글 조합 중 엔터는 글자 확정
  e.preventDefault();
  showSearchResults();
});
document.getElementById("searchBackBtn").addEventListener("click", closeSearch);
document.getElementById("searchClearBtn").addEventListener("click", function () {
  searchViewInput.value = "";
  searchMode = "suggest";
  renderSearch();
  searchViewInput.focus();
});
// ESC: 상세가 위에 있으면 상세만 닫음 → 캡처 단계에서 (상세 닫기 핸들러보다 먼저) 확인
document.addEventListener(
  "keydown",
  function (e) {
    if (e.key === "Escape" && searchOpen && !document.body.classList.contains("detail-open")) closeSearch();
  },
  true,
);

// 홈 검색 버튼 · (모바일) 지도 탭 검색창 → 검색 화면
document.getElementById("homeSearchBtn").addEventListener("click", openSearch);
document.getElementById("searchInput").addEventListener("focus", function () {
  if (mobileQuery.matches) openSearch(); // 검색 화면 입력칸으로 초점이 옮겨감
});

// ----- 마커 탭 → 하단 카드 (가까운 순, 좌우로 넘기면 지도도 그 마커로) -----
// 두 행사 사이 거리 비교값 (정확한 km 는 필요 없고 순서만 — 위도 1도 ≈ 경도 1도 × cos(위도))
function distanceSq(a, b) {
  var dLat = a.latitude - b.latitude;
  var dLng = (a.longitude - b.longitude) * Math.cos((a.latitude * Math.PI) / 180);
  return dLat * dLat + dLng * dLng;
}

// PC 카드 기간 줄 뒤 요약: 진행일 확정이면 "총 N회" 또는 요일 요약(주말 · 매일 …), 아니면 없음 (schedule-format.js)
function pcPeriodSummary(event) {
  if (!hasRunDates(event) || event.startDate === event.endDate) return null;
  if (event.dates.length <= SCHEDULE_LIST_MAX) return I18n.t("sched.times", { n: event.dates.length });
  return weekdaySummary(event) || I18n.t("sched.times", { n: event.dates.length });
}

// PC 배지(진행 중 · 오픈 예정 + D-day) — 상세 칸 · 목록 줄 (11/13 Q4, pc-card.js pcCardBadges)
function pcBadgesHtml(event, className) {
  var badges = pcCardBadges(event, eventStatus, ddayText) // pc-card.js
    .map(function (b) {
      return '<span class="' + b.kind + '">' + escapeHtml(b.text) + "</span>";
    })
    .join("");
  return '<div class="' + className + '">' + badges + "</div>";
}
// PC 기간 줄 "08.01 – 12.19 · 주말" (날짜 필터 중 '일정 확인' 이면 뒤에 붙임)
function pcPeriodText(item) {
  var period = pcCardPeriod(item.event, koreaYear(Date.now()), pcPeriodSummary(item.event)); // pc-card.js
  if (item.runMatch === "maybe") period += " · " + MAYBE_RUN_NOTE;
  return period;
}

// 카드 줄을 다시 그릴 때(찜 · 필터) 카드 줄 안 포커스를 같은 카드 · 같은 버튼으로 되돌림 (Space 찜 뒤 키보드가 이어지게)
function stripFocusSpot() {
  var active = document.activeElement;
  var strip = document.getElementById("previewStrip");
  if (!active || !strip.contains(active)) return null;
  var card = active.closest(".preview-card");
  if (!card) return null;
  return { id: card.dataset.eventId, part: active === card ? null : active.className.split(" ")[0] };
}
function restoreStripFocus(spot) {
  if (!spot) return;
  var card = findPreviewCard(spot.id);
  if (!card) return;
  var target = spot.part ? card.querySelector("." + spot.part) || card : card;
  target.focus({ preventScroll: true });
}

function renderPreviewStrip(sortedItems) {
  var strip = document.getElementById("previewStrip");
  var focusSpot = stripFocusSpot();
  closeRoutePop(false); // 카드(= 창을 연 버튼)를 새로 그리면 창 자리가 사라짐
  strip.innerHTML = "";

  // 같은 장소 행사끼리 붙여서 배치 (같은 장소 1/3 → 2/3 → 3/3), 장소 묶음은 탭한 마커에서 가까운 순
  var groups = [];
  var added = new Set();
  sortedItems.forEach(function (item) {
    var group = item.group || [item];
    if (added.has(group)) return;
    added.add(group);
    groups.push(group);
  });
  if (previewOrigin) {
    groups.sort(function (a, b) {
      return distanceSq(previewOrigin, a[0].event) - distanceSq(previewOrigin, b[0].event);
    });
  }

  groups.forEach(function (group) {
    group.forEach(function (item) {
      var card = document.createElement("article");
      card.className = "preview-card";
      card.dataset.eventId = item.event.id;
      // 같은 장소 i / n 은 카드 밖 띠 #previewPlaceBand 로 (P3 · P1 확정 시안, updatePreviewLabel)
      card.dataset.groupIndex = item.groupIndex || 0;
      card.dataset.groupSize = item.groupSize || 1;
      card.dataset.place = eventPlace(item.event) || "";
      card.innerHTML =
        (item.event.imageUrl
          ? '<img class="preview-poster" src="' + escapeHtml(item.event.imageUrl) + '" alt="" loading="lazy">'
          : defaultPosterHtml(item.event, "preview-poster")) + // 사진이 없으면 기본 그림 (P6)
        '<div class="preview-body">' +
        badgeRowHtml(item.event) +
        '<div class="preview-title">' +
        escapeHtml(item.event.title) +
        "</div>" +
        '<div class="preview-date">' +
        escapeHtml(cardDateText(item)) +
        "</div>" +
        placeLineHtml(item.event, "preview-place") +
        '<div class="preview-actions">' +
        '<button type="button" class="preview-detail">' + escapeHtml(I18n.t("card.detail")) + "</button>" +
        '<button type="button" class="preview-route">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 11 21 3l-8 18-2-8-8-2z" /></svg><span class="route-label">' + escapeHtml(I18n.t("route.title")) + "</span></button>" +
        "</div>" +
        "</div>" +
        favButtonHtml(favoriteEventIds.has(item.event.id));
      posterOnError(card.querySelector("img.preview-poster"), item.event); // 사진을 못 불러오면 기본 그림 (P6)

      function openCardDetail() {
        // 카드 어디를 눌러도 상세 (상세 보기 버튼 포함). 모바일은 상세에서 좌우로 카드 줄 앞뒤 행사 (P3)
        openDetailModal(item.event, mobileQuery.matches ? stripDetailPager(item.event.id) : null);
      }
      card.addEventListener("click", openCardDetail);
      // 키보드: 카드에 초점 → Enter 로 상세 (모바일 카드는 상세 보기 · ♥ 버튼을 숨겨 카드 말고 갈 곳이 없음, #240 검증 ⚠️)
      card.tabIndex = 0;
      card.setAttribute("aria-label", item.event.title);
      card.addEventListener("keydown", function (e) {
        if (e.key !== "Enter" || e.target !== card) return;
        e.preventDefault();
        openCardDetail();
      });
      card.querySelector(".preview-route").addEventListener("click", function (e) {
        e.stopPropagation(); // 길찾기 창만 열고 상세는 열지 않음
        if (mobileQuery.matches) openRouteSheet(item.event); // 모바일 = 하단 시트 그대로
        else toggleRoutePop(item.event, this); // PC = 버튼 위 작은 창 (하단 시트 모양이 PC 에서 깨지던 것)
      });
      card.querySelector(".card-fav").addEventListener("click", function (e) {
        e.stopPropagation();
        toggleFavorite(item.event.id);
      });

      strip.appendChild(card);
    });
  });
  updatePreviewLabel();
  restoreStripFocus(focusSpot);
}

function findPreviewCard(eventId) {
  return document.querySelector(
    '#previewStrip [data-event-id="' + eventId + '"]',
  );
}

// "가까운 순 · 현재 / 전체" (PC 기본 카드 줄은 "목록 순", 모바일은 "밀어서 다음 행사") + 지금 카드 표시(PC: 테두리 강조 · 양옆 흐리게)
// + 카드 줄 위 띠 "이 장소 행사 1 / 6" (같은 장소 행사가 2개 이상일 때만)
function updatePreviewLabel() {
  var strip = document.getElementById("previewStrip");
  var cards = Array.from(strip.children);
  var index = cards.findIndex(function (card) {
    return Number(card.dataset.eventId) === previewEventId;
  });
  cards.forEach(function (card, i) {
    card.classList.toggle("current", i === index);
    if (i === index) card.setAttribute("aria-current", "true"); // 보조기기도 지금 카드를 알게 (#215 검증 ⑦)
    else card.removeAttribute("aria-current");
    card.tabIndex = 0; // 모바일은 카드마다 Enter 로 상세 (#240 P3). 카드 줄은 모바일만 (11/13 Q4)
  });
  document.getElementById("previewLabel").textContent = stripLabelText(!!previewOrigin, index, cards.length, mobileQuery.matches); // strip-nav.js
  var current = cards[index];
  var band = document.getElementById("previewPlaceBand");
  var bandCount = current ? placeBandCount(Number(current.dataset.groupIndex), Number(current.dataset.groupSize)) : ""; // strip-nav.js
  band.hidden = !bandCount;
  if (bandCount) {
    band.innerHTML =
      I18n.t("card.placeBand", { n: "<strong>" + bandCount + "</strong>" }) +
      (current.dataset.place ? '<span class="band-place"> · ' + escapeHtml(current.dataset.place) + "</span>" : "");
  }
  var arrows = stripArrowState(index, cards.length); // strip-nav.js
  document.getElementById("previewPrev").hidden = !arrows.prev;
  document.getElementById("previewNext").hidden = !arrows.next;
}

function previewItem(eventId) {
  return markerList.find(function (it) {
    return it.event.id === eventId;
  });
}

// 같은 장소 다음 카드로 넘기면 지도가 움직이지 않음 → 마커가 한 번 튀어오르고 고리가 퍼져 넘긴 걸 알림 (11/13 확정 시안 P1)
// 카카오 마커는 그림이라 CSS 로 못 움직임 → 같은 그림을 같은 자리 오버레이로 잠깐 띄워 움직이고 원래 마커는 그동안 숨김
var markerHop = null;
function hopMarker(locMarker) {
  stopMarkerHop();
  var box = document.createElement("div");
  box.className = "marker-hop";
  box.style.width = locMarker.bigSize + "px"; // 고른 마커(크게 그린 그림)와 같은 크기
  box.style.height = locMarker.bigSize + "px";
  var ring = document.createElement("span");
  ring.className = "marker-hop-ring";
  var img = document.createElement("img");
  img.className = "marker-hop-icon";
  img.alt = "";
  img.src = locMarker.iconSrc;
  box.appendChild(ring);
  box.appendChild(img);
  var overlay = new kakao.maps.CustomOverlay({
    position: locMarker.marker.getPosition(),
    content: box,
    xAnchor: 0.5,
    yAnchor: 1, // 마커 그림 기준점(아래 가운데)과 같게
    zIndex: 11,
  });
  overlay.setMap(map);
  locMarker.marker.setOpacity(0);
  markerHop = { overlay: overlay, locMarker: locMarker, timer: setTimeout(stopMarkerHop, 900) };
}
function stopMarkerHop() {
  if (!markerHop) return;
  clearTimeout(markerHop.timer);
  markerHop.overlay.setMap(null);
  markerHop.locMarker.marker.setOpacity(1);
  markerHop = null;
}

// 카드를 바꿀 때 마커 강조 + (같은 장소면) 튀어오름
function moveMarkerTo(prevId, nextId) {
  var prev = prevId === null ? null : previewItem(prevId);
  var next = previewItem(nextId);
  selectMarker(nextId, true);
  // 모바일 카드 줄은 그대로 (P3 담당) — PC 만
  var reduce = mobileQuery.matches || window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (next && shouldHopMarker(prev && prev.locMarker, next.locMarker, prevId, nextId, reduce)) hopMarker(next.locMarker); // pc-card.js
}

// 카드가 화면 가운데 오도록 가로 스크롤 (smooth = 화살표 · 휠로 넘길 때 부드럽게)
function scrollStripTo(card, smooth) {
  var strip = document.getElementById("previewStrip");
  var left = card.offsetLeft - (strip.clientWidth - card.offsetWidth) / 2;
  var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (smooth && !reduce) strip.scrollTo({ left: left, behavior: "smooth" });
  else strip.scrollLeft = left;
}

// 화살표 · 휠로 한 장 넘김 → 지도도 바로 그 마커로 (스크롤이 멈추길 기다리지 않아 연달아 눌러도 한 장씩)
function stepPreview(delta) {
  if (previewEventId === null) return;
  var cards = Array.from(document.getElementById("previewStrip").children);
  var index = cards.findIndex(function (card) {
    return Number(card.dataset.eventId) === previewEventId;
  });
  var target = stripStepTarget(index, delta, cards.length); // strip-nav.js
  if (target < 0 || target === index) return;
  var focused = document.activeElement; // 화살표가 숨기 전에 기억
  var prevId = previewEventId;
  previewEventId = Number(cards[target].dataset.eventId);
  closeRoutePop(false); // 창을 연 카드에서 벗어남
  moveMarkerTo(prevId, previewEventId);
  updatePreviewLabel();
  scrollStripTo(cards[target], true);
  // 카드에 포커스가 있었으면(키보드 ← →) 새 지금 카드로 포커스도 옮김
  if (focused && focused.classList && focused.classList.contains("preview-card")) {
    cards[target].focus({ preventScroll: true });
  }
  // 끝에 닿아 누른 화살표가 숨으면 포커스가 사라지지 않게 반대쪽 화살표로 (키보드 사용자)
  var prev = document.getElementById("previewPrev");
  var next = document.getElementById("previewNext");
  if (focused === prev && prev.hidden && !next.hidden) next.focus();
  if (focused === next && next.hidden && !prev.hidden) prev.focus();
}

function showPreview(eventId) {
  var item = markerList.find(function (it) {
    return it.event.id === eventId;
  });
  if (!item) return;
  previewOrigin = item.event;
  previewEventId = eventId;
  renderPreviewStrip(lastSortedItems); // 탭한 마커에서 가까운 순으로 다시 배치
  var card = findPreviewCard(eventId);
  if (!card) return;
  if (mobileQuery.matches && sheetState !== "peek") setSheetState("peek"); // 목록이 올라와 있으면 내려서 카드가 보이게 (모바일)
  document.getElementById("previewArea").classList.add("show");
  refreshToastPlace(); // 같은 장소 띠가 새로 생기면 줄 맨 위가 올라감
  scrollStripTo(card);
  selectMarker(eventId, true);
}

// 지도를 열면 하단 카드 줄을 기본으로 띄움 (PC 11/11 · 모바일 지도 탭 P3, 전엔 마커를 눌러야 뜸)
//  - 순서 · 첫 카드 = 목록 그대로 (찜 먼저 → 시작일 순). 마커 · 목록을 누르면 그때부터 가까운 순 (showPreview)
//  - PC 는 지도를 움직이지 않고(첫 화면 행궁동 그대로) 첫 카드 마커만 강조. 모바일은 첫 카드 마커로 옮김 (카드에 가려 안 보일 수 있어서)
function showDefaultPreview() {
  if (!stripDefaultShown(mobileQuery.matches, lastSortedItems.length, mobileMapShown())) { // strip-nav.js
    hidePreview(); // 0건 · 지도가 가려짐 → 숨김
    return;
  }
  previewOrigin = null;
  renderPreviewStrip(lastSortedItems);
  var first = document.getElementById("previewStrip").firstElementChild;
  previewEventId = Number(first.dataset.eventId);
  updatePreviewLabel();
  document.getElementById("previewArea").classList.add("show");
  refreshToastPlace();
  scrollStripTo(first);
  selectMarker(previewEventId, mobileQuery.matches);
}

function hidePreview() {
  if (previewEventId === null) return;
  previewEventId = null;
  previewOrigin = null;
  document.getElementById("previewArea").classList.remove("show");
  refreshToastPlace();
  selectMarker(null);
}

// 선택된 행사가 있는 장소 마커만 크게 + 맨 위로
function selectMarker(eventId, pan) {
  if (selectedLocMarker) {
    selectedLocMarker.marker.setImage(selectedLocMarker.normalImage);
    selectedLocMarker.marker.setZIndex(0);
    selectedLocMarker = null;
  }
  if (eventId === null) return;

  var item = markerList.find(function (it) {
    return it.event.id === eventId;
  });
  if (!item || !item.locMarker) return;
  var target = item.locMarker; // 같은 장소 행사들은 마커 1개를 같이 씀
  target.marker.setImage(target.selectedImage);
  target.marker.setZIndex(10);
  selectedLocMarker = target;
  if (pan) map.panTo(target.marker.getPosition());
}

// 카드를 좌우로 넘기고 멈추면 → 가운데 카드의 마커로 지도 이동 (순서는 처음 탭한 마커 기준 그대로)
// 고른 카드가 줄 안에 다 보이면 그대로 둠 (넓은 PC 줄에서 첫 · 끝 카드가 옆 카드로 바뀌지 않게 — strip-nav.js)
var stripScrollTimer = null;
document
  .getElementById("previewStrip")
  .addEventListener("scroll", function () {
    if (routePopEvent) closeRoutePop(false); // 카드 줄이 밀리면 창을 연 버튼도 움직임 (#241 검증)
    clearTimeout(stripScrollTimer);
    stripScrollTimer = setTimeout(function () {
      if (previewEventId === null) return;
      var strip = document.getElementById("previewStrip");
      var cards = Array.from(strip.children);
      var current = cards.findIndex(function (card) {
        return Number(card.dataset.eventId) === previewEventId;
      });
      var boxes = cards.map(function (card) {
        return { left: card.offsetLeft, width: card.offsetWidth };
      });
      var index = stripSettleIndex(current, boxes, strip.scrollLeft, strip.clientWidth);
      if (index < 0) return;
      var id = Number(cards[index].dataset.eventId);
      if (id !== previewEventId) {
        var prevId = previewEventId;
        previewEventId = id;
        closeRoutePop(false);
        moveMarkerTo(prevId, id);
        updatePreviewLabel();
      }
    }, 120);
  });

// 마우스 PC: 좌우 화살표 · 세로 휠로 한 장씩 (터치 · 키보드 · 트랙패드 좌우는 기본 가로 스크롤)
document.getElementById("previewPrev").addEventListener("click", function () {
  stepPreview(-1);
});
document.getElementById("previewNext").addEventListener("click", function () {
  stepPreview(1);
});
// 키보드 ← → : 한 장씩 (상세 · 길찾기 · 공유 창 · 검색 화면이 열렸으면 그대로)
//  - 모바일 하단 카드가 떠 있으면 포커스가 어디 있든 (P3 담당). PC 는 카드 줄이 없음 → 상세 칸 키 처리(pcKeyAction, 11/13 Q4)
document.addEventListener("keydown", function (e) {
  if (!mobileQuery.matches) return;
  if (previewEventId === null || !document.getElementById("previewArea").classList.contains("show")) return;
  if (document.body.classList.contains("detail-open") || searchOpen) return;
  if (!document.getElementById("routeSheet").hidden || !document.getElementById("shareSheet").hidden) return;
  var el = document.activeElement || document.body;
  var step = stripKeyStep({
    key: e.key,
    tag: el.tagName,
    editable: el.isContentEditable,
    role: el.getAttribute("role"),
    alt: e.altKey || e.ctrlKey || e.metaKey || e.shiftKey,
    handled: e.defaultPrevented,
  }); // strip-nav.js
  if (step === 0) return;
  e.preventDefault(); // 카드 줄 기본 가로 스크롤과 겹쳐 두 장 넘어가지 않게
  stepPreview(step);
});

var stripWheelAt = 0;
document.getElementById("previewStrip").addEventListener(
  "wheel",
  function (e) {
    if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return; // 가로 휠(트랙패드 좌우) → 기본 스크롤
    e.preventDefault(); // 세로 휠이 카드 줄에서는 페이지 · 지도를 움직이지 않게
    var now = Date.now();
    var step = wheelStep(e.deltaX, e.deltaY, stripWheelAt, now); // strip-nav.js
    if (step === 0) return;
    stripWheelAt = now;
    stepPreview(step);
  },
  { passive: false },
);

// ===== PC 상세 칸 (11/13 확정 시안 Q4 PcNavD1 — docs/design/2026-11-13-todo-additions.md "Q4") =====
// 지도 | 상세 440px(늘 열림) | 목록 340px. 처음엔 목록 첫 행사, 넘기는 순서 = 오른쪽 목록 순서 (탭 · 분류 · 찜 정렬 그대로)
// 넘기면: 목록 고른 줄 표시 + 보이게 스크롤, 지도 핀은 크게 + 화면 밖이면 지도 이동. 키보드 포커스는 있던 자리 그대로
// 키가 먹는 범위는 pc-card.js pcKeyZone · pcKeyAction (시안 문서 표 그대로, node 시험)
var pcDetailId = null; // 상세 칸에 보이는 행사 id (목록이 비면 null)
var pcOutsideId = null; // 공유 링크 · 검색처럼 목록 밖(종료 등)에서 고른 행사 — 다른 행사로 넘기기 전까지 칸에 둠
var pcRenderedId = null; // 포스터 · 아래 정보를 마지막으로 그린 행사 (찜 · 필터로 다시 그릴 때 사진이 깜빡이지 않게)
var pcSettleTimer = null;
var PC_SETTLE_MS = 600; // 넘기다 멈추면 정류장 · 역을 불러오고 '최근 본' 에 기록 (← → 를 연달아 눌러도 요청이 몰리지 않게)
var PC_FAV_TOAST_MS = 1600; // 시안: 찜할 때만 "♥ 찜했어요" 1.6초
var pcTransitTicket = 0;
var pcUserPicked = false; // 사용자가 고른 행사인가 (목록 · 마커 · ‹ › · 키보드 · 공유 링크) — 자동 선택(첫 로드 · 필터 뒤 첫 행사)은 '최근 본' 에 넣지 않음 (#256 검증 ⚠️)

function pcOrderIds() {
  return lastSortedItems.map(function (item) {
    return item.event.id;
  });
}
function pcItem(id) {
  return (
    lastSortedItems.find(function (item) {
      return item.event.id === id;
    }) || previewItem(id)
  );
}
function prefersReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// 목록을 다시 그린 뒤(필터 · 찜 · 정렬): 보던 행사가 목록에 남아 있으면 그대로, 빠졌으면 목록 첫 행사 (지도는 옮기지 않음)
function pcSyncList(focusRowId) {
  var ids = pcOrderIds();
  var keep = pcDetailId !== null && (ids.indexOf(pcDetailId) >= 0 || (pcDetailId === pcOutsideId && previewItem(pcDetailId)));
  if (!keep) {
    pcDetailId = ids.length ? ids[0] : null;
    pcOutsideId = null;
    pcUserPicked = false;
  }
  renderPcDetail();
  pcMarkRows(false);
  selectMarker(pcDetailId, false);
  pcCenterFirst();
  if (focusRowId !== null) {
    var row = pcRow(focusRowId);
    if (row) {
      row.focus({ preventScroll: true });
      row.scrollIntoView({ block: "nearest" }); // Space 찜 → 맨 위 찜 묶음으로 옮겨 가도 포커스 줄이 보이게 (#256 검증 ⚠️)
    }
  }
}

// 행사 고르기 (목록 줄 · 마커 · ‹ › · 키보드). opts = { announce(보조기기에 "3 / 6 · 제목"), pan(핀이 화면 밖이면 지도 이동),
//   focusRow(목록 줄에 있던 포커스를 새 줄로 — ↑ ↓ · ← →), outside(목록 밖 행사도 칸에 둠) }
function pcSelect(id, opts) {
  opts = opts || {};
  var item = pcItem(id);
  if (!item) return;
  var prevId = pcDetailId;
  pcDetailId = id;
  pcOutsideId = opts.outside && pcOrderIds().indexOf(id) < 0 ? id : null;
  pcUserPicked = true;
  pcAutoCenter = false; // 사용자가 고르면 첫 진입 중심 맞추기는 끝
  if (prevId !== id) closeRoutePop(false); // 창을 연 [길찾기] 는 그대로지만 행사가 바뀜
  renderPcDetail();
  pcMarkRows(prevId !== id, opts.focusRow);
  pcShowMarker(prevId, id, opts.pan !== false);
  if (opts.announce) pcAnnounce();
}

// ‹ › · 키보드 넘기기 (처음 · 끝에서 멈춤 — pc-card.js pcStepId)
function pcStep(delta, opts) {
  var id = pcStepId(pcOrderIds(), pcDetailId, delta); // pc-card.js
  if (id === null) return;
  pcSelect(id, { announce: true, pan: true, focusRow: opts && opts.focusRow });
}

// 넘길 때 aria-live 한 곳에서만 "3 / 6 · 제목" (토스트와 겹치지 않게)
function pcAnnounce() {
  var item = pcItem(pcDetailId);
  if (!item) return;
  var ids = pcOrderIds();
  var index = ids.indexOf(pcDetailId);
  var live = document.getElementById("pcdLive");
  live.textContent =
    index < 0
      ? item.event.title
      : I18n.t("pcd.live", { i: index + 1, n: ids.length, title: item.event.title });
}

// 마커: 고른 행사 핀을 크게, 화면 밖이면 지도를 옮김. 같은 장소의 다음 행사면 핀이 한 번 튀어오름 (P1 그대로)
function pcShowMarker(prevId, id, pan) {
  selectMarker(id, false);
  var next = pcItem(id);
  if (!next || !next.locMarker || !map) return;
  var reduce = prefersReducedMotion();
  if (pan) pcMoveMap(next.locMarker.marker.getPosition(), reduce);
  var prev = prevId === null ? null : pcItem(prevId);
  if (shouldHopMarker(prev && prev.locMarker, next.locMarker, prevId, id, reduce)) hopMarker(next.locMarker); // pc-card.js
}

// ===== PC 지도 이동 · 확대 단추 (11/13 확정 R6 · R7 · B1 — docs/design/2026-11-13-pc-pending.md "③ 지도 · 키") =====

// B1: 첫 진입 때 지도 중심 = 상세 칸 첫 행사 (찜한 사람은 찜 첫 행사 — 찜 목록이 늦게 오면 그때 한 번 더)
//  - 사용자가 행사를 고르거나 지도를 만지면(끌기 · 휠 · + / −) 끝. 공유 링크(?event=)로 들어오면 그 행사가 중심이라 안 함
var pcAutoCenter = !initialParams.get("event");
var pcFavoritesReady = false; // 로그인 확인 · 찜 목록 받기가 끝났나 (끝난 뒤 한 번 맞추면 자동 중심도 끝)
function pcCenterFirst() {
  if (!pcAutoCenter || pcUserPicked || pcDetailId === null || !map || mobileQuery.matches) return;
  var item = pcItem(pcDetailId);
  if (item && item.event.latitude && item.event.longitude) {
    map.setCenter(new kakao.maps.LatLng(item.event.latitude, item.event.longitude));
  }
  if (eventsLoaded && pcFavoritesReady) pcAutoCenter = false;
}

// R7: 고른 행사로 지도 이동 — 화면 안이면 그대로 · 바로 옆이면 panTo · 한 칸 넘게 멀면 축소(0.3초) → panTo → 원래 레벨로(0.3초)
//  - 카카오 지도는 확대 · 축소 움직임 도중에 들어온 setLevel · panTo 를 무시함 (11/13 화면 시험) → 정해 둔 시간이 아니라
//    움직임이 다 멈췄다는 이벤트(idle)를 받고 다음 단계로. 이벤트가 안 오면 PC_MOVE_WAIT 뒤 넘어감
//    (zoom_changed 는 idle 보다 먼저 와서, 그때 panTo 를 하면 바로 뒤 축소의 idle 을 panTo 끝으로 잘못 받음)
//  - 움직이는 도중 다른 행사를 고르면 목적지만 바꿈 → 남은 단계가 새 행사로 감 (돌아갈 레벨은 처음 레벨 그대로)
//  - 움직임 줄이기 설정이면 setCenter 로 한 번에
var PC_MOVE_WAIT = 2500;
var pcMove = { active: false, base: null, target: null, panned: null, stop: null };
function pcMoveCancel() {
  if (pcMove.stop) pcMove.stop();
  pcMove.stop = null;
  pcMove.active = false;
}
// 지도가 멈출 때(idle) 한 번 기다리기 (안 오면 PC_MOVE_WAIT 뒤 그냥 다음 단계). 다음 명령은 이벤트 처리가 끝난 뒤에
function pcMoveAfter(next) {
  var eventName = "idle";
  var done = false;
  function go() {
    if (done) return;
    done = true;
    kakao.maps.event.removeListener(map, eventName, go);
    clearTimeout(timer);
    pcMove.stop = null;
    setTimeout(next, 0);
  }
  var timer = setTimeout(go, PC_MOVE_WAIT);
  kakao.maps.event.addListener(map, eventName, go);
  pcMove.stop = function () {
    done = true;
    kakao.maps.event.removeListener(map, eventName, go);
    clearTimeout(timer);
  };
}
function pcMoveMap(position, reduce) {
  if (reduce) {
    var base = pcMove.active ? pcMove.base : null;
    pcMoveCancel();
    if (base !== null) map.setLevel(base);
    if (base !== null || !map.getBounds().contain(position)) map.setCenter(position);
    return;
  }
  if (pcMove.active) {
    pcMove.target = position; // 남은 단계가 새 행사로
    return;
  }
  var b = map.getBounds();
  var kind = pcMoveKind(
    { south: b.getSouthWest().getLat(), west: b.getSouthWest().getLng(), north: b.getNorthEast().getLat(), east: b.getNorthEast().getLng() },
    { lat: position.getLat(), lng: position.getLng() },
  ); // pc-card.js
  if (kind === "inside") return;
  if (kind === "near") {
    map.panTo(position);
    return;
  }
  pcMove.active = true;
  pcMove.base = map.getLevel();
  pcMove.target = position;
  var out = Math.min(pcMove.base + PC_MOVE_OUT, PC_ZOOM_MAX);
  if (out === pcMove.base) pcMovePan();
  else {
    pcMoveAfter(pcMovePan);
    map.setLevel(out, { animate: { duration: PC_MOVE_MS.out } });
  }
}
function pcMovePan() {
  pcMove.panned = pcMove.target;
  pcMoveAfter(pcMoveBack);
  map.panTo(pcMove.target);
}
function pcMoveBack() {
  if (pcMove.target !== pcMove.panned) return pcMovePan(); // 옮기는 사이 다른 행사를 고름 → 그쪽으로 다시
  if (map.getLevel() === pcMove.base) return pcMoveDone();
  pcMoveAfter(pcMoveDone);
  map.setLevel(pcMove.base, { animate: { duration: PC_MOVE_MS.back } });
}
function pcMoveDone() {
  var again = pcMove.target !== pcMove.panned ? pcMove.target : null; // 확대하는 사이 다른 행사를 고름
  // 안전망: 앞 panTo 의 idle 을 축소 끝으로 받는 등 명령이 무시돼 고른 핀이 화면 밖에 남았으면 한 번에 옮김 (#262 검증 ⚠️)
  if (!again && !map.getBounds().contain(pcMove.target)) map.setCenter(pcMove.target);
  pcMoveCancel();
  if (again) pcMoveMap(again, prefersReducedMotion());
}

// R6: 지도 오른쪽 아래 + / − (PC 만 보임). 가장 크게 · 작게면 그 단추 aria-disabled
//  - 레벨 범위 PC_ZOOM_MIN · PC_ZOOM_MAX 는 pc-card.js (kakao.maps.load 가 이 파일 실행 중에 바로 불릴 수 있어 먼저 읽히는 파일에 둠)
function setupPcZoom() {
  var zoomIn = document.getElementById("mapZoomIn");
  var zoomOut = document.getElementById("mapZoomOut");
  function stopAutoCenter() {
    pcAutoCenter = false;
  }
  function refresh() {
    var state = pcZoomState(map.getLevel(), PC_ZOOM_MIN, PC_ZOOM_MAX); // pc-card.js
    zoomIn.setAttribute("aria-disabled", String(!state.zoomIn));
    zoomOut.setAttribute("aria-disabled", String(!state.zoomOut));
  }
  function zoomBy(delta) {
    stopAutoCenter();
    var level = map.getLevel() + delta;
    if (level < PC_ZOOM_MIN || level > PC_ZOOM_MAX) return;
    map.setLevel(level, { animate: true });
    refresh(); // zoom_changed 는 움직임이 끝난 뒤라 단추 상태를 바로 맞춤
  }
  zoomIn.addEventListener("click", function () {
    zoomBy(-1);
  });
  zoomOut.addEventListener("click", function () {
    zoomBy(1);
  });
  kakao.maps.event.addListener(map, "zoom_changed", refresh);
  refresh();
  var box = document.getElementById("map");
  box.addEventListener("pointerdown", stopAutoCenter, true);
  box.addEventListener("wheel", stopAutoCenter, { capture: true, passive: true });
}

// 오른쪽 목록 줄: 고른 줄 표시(바탕 · ‹ · aria-current) + 롤링 포커스(목록에 Tab 으로 한 번만 들어가고 안에서는 ↑ ↓)
function pcRow(id) {
  return document.querySelector('#list .event-item[data-event-id="' + id + '"]');
}
function pcMarkRows(scroll, focusRow) {
  var rows = document.querySelectorAll("#list .event-item");
  var selected = null;
  rows.forEach(function (row) {
    var on = Number(row.dataset.eventId) === pcDetailId;
    row.classList.toggle("selected", on);
    if (on) {
      row.setAttribute("aria-current", "true");
      selected = row;
    } else {
      row.removeAttribute("aria-current");
    }
    row.tabIndex = -1;
    row.setAttribute("aria-describedby", "kbdHint");
  });
  var entry = selected || rows[0];
  if (entry) entry.tabIndex = 0;
  if (selected && scroll) selected.scrollIntoView({ block: "nearest", behavior: prefersReducedMotion() ? "auto" : "smooth" });
  if (selected && focusRow) selected.focus({ preventScroll: true });
}
function pcFocusedRowId() {
  var el = document.activeElement;
  if (mobileQuery.matches || !el || !el.classList || !el.classList.contains("event-item")) return null;
  return document.getElementById("list").contains(el) ? Number(el.dataset.eventId) : null;
}

// 상세 칸 그리기. 같은 행사를 다시 그릴 땐(찜 · 필터) 포스터 · 아래 정보 · 스크롤 자리는 그대로
function renderPcDetail() {
  var pane = document.getElementById("pcDetail");
  var focusInPane = pane.contains(document.activeElement) && document.activeElement !== pane;
  drawPcDetail();
  // 상세 칸 아래쪽([주소 복사] · 캘린더 · 버스 +N)이나 숨겨진 [원문 보기]에 있던 포커스가 사라지면 칸 자체로 (#256 검증 ⚠️ — 포커스는 그 자리)
  var active = document.activeElement;
  if (focusInPane && (!active || active === document.body || !pane.contains(active) || active.offsetParent === null)) {
    pane.focus({ preventScroll: true });
  }
}
function drawPcDetail() {
  var item = pcDetailId === null ? null : pcItem(pcDetailId);
  var scroll = document.getElementById("pcdScroll");
  var empty = document.getElementById("pcdEmpty");
  var hint = document.getElementById("kbdHint");
  if (!item) {
    pcDetailId = null;
    pcRenderedId = null;
    scroll.hidden = true;
    hint.hidden = true;
    empty.hidden = false;
    empty.innerHTML = eventsLoaded
      ? "<strong>" + escapeHtml(I18n.t("pcd.emptyTitle")) + "</strong><p>" + escapeHtml(I18n.t("pcd.emptyBody")) + "</p>"
      : "<p>" + escapeHtml(I18n.t(eventsLoadFailed ? "load.failedShort" : "load.loading")) + "</p>";
    return;
  }
  var event = item.event;
  scroll.hidden = false;
  hint.hidden = false;
  empty.hidden = true;
  var ids = pcOrderIds();

  if (pcRenderedId !== event.id) {
    pcRenderedId = event.id;
    var poster = document.getElementById("pcdPoster");
    var img = cardImageUrl(event);
    poster.innerHTML = img
      ? '<img class="pcd-img" src="' + escapeHtml(img) + '" alt="">'
      : defaultPosterHtml(event, "pcd-img"); // 사진이 없으면 기본 그림 (P6)
    posterOnError(poster.querySelector("img.pcd-img"), event);
    renderPcMore(event);
    scroll.scrollTop = 0;
    clearTimeout(pcSettleTimer);
    var picked = pcUserPicked;
    pcSettleTimer = setTimeout(function () {
      if (pcDetailId !== event.id) return;
      loadPcTransit(event);
      if (picked) rememberRecentView(event.id); // 찜 탭 "최근 본" — 사용자가 고르거나 넘기다 멈춘 행사만
    }, PC_SETTLE_MS);
  }

  // 포스터 아래 ‹ 3 / 30 › 줄 (처음 · 끝이면 그쪽 단추 화살표만 흐리게 — 포커스는 남김)
  var pager = pcPagerState(ids, event.id); // pc-card.js
  document.getElementById("pcdPos").textContent = pager.text;
  document.getElementById("pcdPrev").setAttribute("aria-disabled", pager.canPrev ? "false" : "true");
  document.getElementById("pcdNext").setAttribute("aria-disabled", pager.canNext ? "false" : "true");

  // 배지 → 제목 → 기간 · 시간 · 장소 · 요금 (값이 있는 줄만)
  document.getElementById("pcdBadges").outerHTML = pcBadgesHtml(event, "pcd-badges").replace('<div class="pcd-badges">', '<div id="pcdBadges" class="pcd-badges">');
  document.getElementById("pcdTitle").textContent = event.title;
  var lines = infoLines(event.info);
  var fields = detailFields(event);
  var rows = [];
  rows.push([I18n.t("detail.period"), pcPeriodText(item)]);
  var time = findInfo(lines, ["시간", "요일"]) || fallbackTime(event);
  if (time) rows.push([I18n.t("detail.time"), time]);
  var place = pcCardPlace(eventPlace(event), eventDistrict(event)); // pc-card.js
  if (place) rows.push([I18n.t("detail.place"), place]);
  var fee = fields.fee || findInfo(lines, ["이용료", "요금", "입장료", "관람료", "참가비"], ["주차"]);
  if (fee) rows.push([I18n.t("detail.fee"), fee]);
  var sourceText = sourceLabel(event); // 인스타 · 수원문화DB 출처 줄 (수원문화DB 는 출처 표기 필수, docs/decisions/2026-11-13-swdb-crawling.md)
  if (sourceText) rows.push([I18n.t("detail.source"), sourceText]);
  document.getElementById("pcdRows").innerHTML = rows
    .map(function (row) {
      return "<dt>" + escapeHtml(row[0]) + "</dt><dd>" + escapeHtml(row[1]) + "</dd>";
    })
    .join("");

  // [♡ 찜] [길찾기] [원문 보기]
  updatePcFav();
  var source = document.getElementById("pcdSource");
  var link = safeLink(event.link);
  source.hidden = !link;
  if (link) {
    source.href = link;
    source.textContent = I18n.t(isInstagramEvent(event) ? "detail.instagram" : "pcd.source");
  }
}

function updatePcFav() {
  var btn = document.getElementById("pcdFav");
  if (!btn || pcDetailId === null) return;
  var on = favoriteEventIds.has(pcDetailId);
  btn.textContent = I18n.t(on ? "pcd.faved" : "pcd.fav");
  btn.classList.toggle("active", on);
  btn.setAttribute("aria-pressed", on ? "true" : "false");
  btn.title = I18n.t(on ? "fav.unfav" : "fav.fav") + " (Space)";
}

// 찜 토스트 (PC): 찜할 때만 "♥ 찜했어요" 1.6초, 해제 · 넘기기는 토스트 없음 (시안 PcNavD1)
function pcFavToast(added) {
  if (added) showToast(I18n.t("pcd.favToast"), null, null, PC_FAV_TOAST_MS);
  else hideToast();
}

// 단추 아래 나머지 정보: 소개 · 위치(주소 복사 · 가까운 정류장 · 역) · 캘린더 — 칸 안에서 스크롤
function renderPcMore(event) {
  var more = document.getElementById("pcdMore");
  var html = "";
  if (event.info) {
    html += '<section class="pcd-sec"><h3>' + escapeHtml(I18n.t("detail.intro")) + '</h3><p class="pcd-intro">' + escapeHtml(event.info) + "</p></section>";
  }
  var address = extractAddress(event.info) || eventPlace(event) || "";
  html +=
    '<section class="pcd-sec"><h3>' + escapeHtml(I18n.t("detail.location")) + '</h3><div class="pcd-addr"><span>' +
    escapeHtml(address || I18n.t("detail.noLocation")) + "</span>" +
    (address ? '<button type="button" class="pcd-copy">' + escapeHtml(I18n.t("route.copy")) + "</button>" : "") +
    '</div><div id="pcdTransit" class="pcd-transit" hidden></div></section>';
  if (event.startDate) {
    // 구글 캘린더 "일정 추가" (진행 날짜를 고르는 행사는 기본 날짜 하루 — 전 PC 상세 창과 같은 주소)
    var choices = calendarDateChoices(event);
    var query = choices ? "?date=" + encodeURIComponent(choices.selected) : "";
    html +=
      '<a class="pcd-cal" href="/api/events/' + encodeURIComponent(event.id) + "/calendar/google" + query +
      '" target="_blank" rel="noopener">' + escapeHtml(I18n.t("detail.calendar")) + "</a>";
  }
  more.innerHTML = html;
  var copy = more.querySelector(".pcd-copy");
  if (copy) {
    copy.addEventListener("click", function () {
      copyText(address).then(
        function () {
          showToast(I18n.t("toast.addrCopied"));
        },
        function () {
          showToast(I18n.t("toast.copyFailedSelect"));
        },
      );
    });
  }
}

// 가까운 정류장 · 역 (transit-nearby.js) — 넘기다 멈춘 행사만 불러옴 (경기도 API 하루 한도 아낌), 비거나 실패면 숨김
function loadPcTransit(event) {
  var box = document.getElementById("pcdTransit");
  var ticket = ++pcTransitTicket;
  if (!box || !event.latitude || !event.longitude) return;
  fetch("/api/events/" + event.id + "/transit")
    .then(function (res) {
      return res.ok ? res.json() : null;
    })
    .then(function (data) {
      var target = document.getElementById("pcdTransit");
      if (ticket !== pcTransitTicket || pcDetailId !== event.id || !target) return;
      var html = transitNearbyHtml(data);
      if (!html) return;
      target.innerHTML = html;
      target.hidden = false;
    })
    .catch(function () {});
}
document.getElementById("pcdMore").addEventListener("click", function (e) {
  var more = e.target.closest(".transit-more");
  if (more) expandTransitRoutes(more);
});

// ‹ › (처음 · 끝은 aria-disabled — 눌러도 그대로, 포커스는 남김)
document.getElementById("pcdPrev").addEventListener("click", function () {
  pcStep(-1);
});
document.getElementById("pcdNext").addEventListener("click", function () {
  pcStep(1);
});
document.getElementById("pcdFav").addEventListener("click", function () {
  if (pcDetailId !== null) toggleFavorite(pcDetailId);
});
document.getElementById("pcdRoute").addEventListener("click", function () {
  var item = pcItem(pcDetailId);
  if (item) toggleRoutePop(item.event, this); // 단추 위 작은 창 (P1 그대로)
});
// 상세 칸을 스크롤하면 [길찾기] 단추가 움직임 → 단추 위 작은 창은 닫음 (#256 검증 ⚠️, 카드 줄 때와 같은 규칙)
document.getElementById("pcdScroll").addEventListener("scroll", function () {
  if (routePopEvent) closeRoutePop(false);
});
// 툴팁: Esc 로 닫고, 마우스가 단추에 새로 올라오면 다시 보임 (WCAG 1.4.13)
document.querySelectorAll("#pcDetail .pcd-nav").forEach(function (nav) {
  nav.addEventListener("mouseenter", function () {
    document.getElementById("pcDetail").classList.remove("tips-off");
  });
});

// 작은 창 · 공유 창 · ☰ 메뉴 · 언어 목록 · 달력이 열려 있나 → 열려 있으면 ← → · Space 로 넘기거나 찜하지 않음
function pcPopupOpen() {
  return (
    !!routePopEvent ||
    !document.getElementById("routeSheet").hidden ||
    !document.getElementById("shareSheet").hidden ||
    document.getElementById("topbar").classList.contains("menu-open") ||
    !document.getElementById("langPop").hidden ||
    document.body.classList.contains("lang-sheet-open") ||
    catMenuOpen || // 분류 펼침 목록 (R5 A4)
    calOpen ||
    calPickerOpen
  );
}

// PC 키보드: ← → 넘기기 · Space 찜 · 목록 줄 ↑ ↓ · Enter 상세 칸으로 (범위 = pc-card.js pcKeyAction)
//  - Space 는 지도 · 목록 스크롤 영역 · 빈 곳 · 상세 칸 본문에서도 상세 행사 찜 (11/13 확정 B3). 입력칸 · 단추 · 열린 창은 그대로
document.addEventListener("keydown", function (e) {
  if (mobileQuery.matches) return;
  if (e.key === "Escape") {
    document.getElementById("pcDetail").classList.add("tips-off");
    return;
  }
  if (pcDetailId === null) return;
  var el = document.activeElement || document.body;
  var pane = document.getElementById("pcDetail");
  var zone = pcKeyZone({
    tag: el.tagName,
    editable: el.isContentEditable,
    role: el.getAttribute ? el.getAttribute("role") : null,
    row: !!(el.classList && el.classList.contains("event-item") && document.getElementById("list").contains(el)),
    pane: el === pane,
  }); // pc-card.js
  var action = pcKeyAction({
    key: e.key,
    zone: zone,
    popup: pcPopupOpen(),
    mods: e.altKey || e.ctrlKey || e.metaKey || e.shiftKey,
    handled: e.defaultPrevented,
    composing: e.isComposing || e.keyCode === 229,
  }); // pc-card.js
  if (!action) return;
  e.preventDefault(); // Space 가 칸을 내리지 않게, ↑ ↓ 가 목록을 스크롤하지 않게
  if (action === "fav") toggleFavorite(zone === "row" ? Number(el.dataset.eventId) : pcDetailId);
  else if (action === "focusPane") pane.focus();
  else pcStep(action === "next" ? 1 : -1, { focusRow: zone === "row" });
});

// ----- 토스트 (찜 · 주소 복사 · 링크 복사 안내, 2.5초 뒤 자동으로 사라짐) -----
var toastTimer = null;
// duration = 사라질 때까지 ms (없으면 버튼 있는 토스트 4초 · 글만 2.5초)
function showToast(message, actionLabel, onAction, duration) {
  var toast = document.getElementById("toast");
  var action = document.getElementById("toastAction");
  document.getElementById("toastText").textContent = message;
  action.hidden = !actionLabel;
  action.textContent = actionLabel || "";
  action.onclick = function () {
    hideToast();
    if (onAction) onAction();
  };
  toast.hidden = false;
  placeToast(toast);
  toastDuration = duration || (actionLabel ? 4000 : 2500); // 버튼 있는 토스트는 누를 시간을 더 줌
  clearTimeout(toastTimer);
  toastTimer = setTimeout(hideToast, toastDuration);
}
var toastDuration = 2500;
// PC: 토스트는 가운데 상세 칸 가운데, 칸 바닥에서 64px 위 = 키 표시 줄 위 (11/13 Q4 시안 PcNavD1). 모바일은 CSS 자리(하단 탭 · 상세 바 위) 그대로
var PC_TOAST_BOTTOM = 64;
function placeToast(toast) {
  var pane = mobileQuery.matches ? null : document.getElementById("pcDetail").getBoundingClientRect();
  if (!pane || !pane.width) {
    toast.style.left = "";
    toast.style.bottom = "";
    return;
  }
  toast.style.left = Math.round(pane.left + pane.width / 2) + "px";
  toast.style.bottom = Math.round(window.innerHeight - pane.bottom + PC_TOAST_BOTTOM) + "px";
}
// 토스트가 떠 있는 동안 카드 줄 · 상세창 · 화면 폭이 바뀌면 자리를 다시 맞춤 (#249 검증 ② ④ —
// PC → 모바일로 줄이면 PC 용 left · bottom 이 모바일 자리를 덮어쓰지 않게)
function refreshToastPlace() {
  var toast = document.getElementById("toast");
  if (!toast.hidden) placeToast(toast);
}
mobileQuery.addEventListener("change", refreshToastPlace);
// 마우스를 올리거나 키보드로 버튼에 가 있는 동안은 사라지지 않게
function pauseToast() {
  clearTimeout(toastTimer);
}
function resumeToast() {
  if (document.getElementById("toast").hidden) return;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(hideToast, toastDuration);
}
["mouseenter", "focusin"].forEach(function (type) {
  document.getElementById("toast").addEventListener(type, pauseToast);
});
["mouseleave", "focusout"].forEach(function (type) {
  document.getElementById("toast").addEventListener(type, resumeToast);
});
function hideToast() {
  clearTimeout(toastTimer);
  document.getElementById("toast").hidden = true;
}

// 클립보드: 보안 컨텍스트(https · localhost)가 아니면 navigator.clipboard 가 없음 → 옛 방식으로
function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text);
  }
  return new Promise(function (resolve, reject) {
    var area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.cssText = "position:fixed;top:0;left:0;opacity:0";
    document.body.appendChild(area);
    area.select();
    var ok = false;
    try {
      ok = document.execCommand("copy");
    } catch (e) {}
    document.body.removeChild(area);
    ok ? resolve() : reject(new Error("copy failed"));
  });
}

// ----- 길찾기 창 (하단 시트): 행사명 · 장소 · 주소 복사 · 카카오맵 · 네이버 · Google Maps -----
var routeEvent = null;
function openRouteSheet(event) {
  routeEvent = event;
  var place = eventPlace(event) || event.title;
  var address = extractAddress(event.info);
  document.getElementById("routeEvent").textContent = event.title;
  document.getElementById("routePlace").textContent = place;
  document.getElementById("routeAddress").textContent = address || I18n.t("route.noAddress");
  document.getElementById("routeCopyBtn").hidden = !address;
  document.getElementById("routeKakao").href = kakaoRouteUrl(place, event.latitude, event.longitude);
  document.getElementById("routeNaver").href = naverRouteUrl(place, event.latitude, event.longitude);
  document.getElementById("routeGoogle").href = googleRouteUrl(event.latitude, event.longitude);
  document.getElementById("routeSheet").hidden = false;
  dialogFocus.opened("route", document.querySelector("#routeSheet .route-sheet"));
  nav.open("route");
  syncUrlState();
}
function closeRouteSheet() {
  routeEvent = null;
  document.getElementById("routeSheet").hidden = true;
  dialogFocus.closed("route");
  nav.close("route");
  syncUrlState();
}
// ----- PC 길찾기 작은 창 (11/13 확정 시안 P1): 하단 카드 [길찾기] 버튼 위 300px 창 -----
// 전엔 PC 에서도 위 하단 시트를 열었는데 시트 모양 CSS 가 모바일 구간에만 있어 글자가 지도 위에 흩어졌음
// 모바일 하단 시트는 그대로. 주소(?route=1) 기록은 모바일만 (PC 는 지도 앱으로 넘어가지 않고 새 탭)
var routePopEvent = null;
var routePopButton = null;
function toggleRoutePop(event, button) {
  if (routePopEvent && routePopButton === button) {
    closeRoutePop(true);
    return;
  }
  closeRoutePop(false);
  routePopEvent = event;
  routePopButton = button;
  var place = eventPlace(event) || event.title;
  var address = extractAddress(event.info);
  document.getElementById("routePopAddr").textContent = address ? address + " · " + place : place;
  document.getElementById("routePopCopy").hidden = !address;
  document.getElementById("routePopKakao").href = kakaoRouteUrl(place, event.latitude, event.longitude); // route-links.js
  document.getElementById("routePopNaver").href = naverRouteUrl(place, event.latitude, event.longitude);
  var pop = document.getElementById("routePop");
  pop.hidden = false;
  placeRoutePop();
  button.setAttribute("aria-expanded", "true");
  button.classList.add("open");
  button.querySelector(".route-label").textContent = I18n.t("route.title") + " ▴";
  dialogFocus.opened("routePop", pop);
}
function placeRoutePop() {
  if (!routePopButton) return;
  var pop = document.getElementById("routePop");
  var rect = routePopButton.getBoundingClientRect();
  var spot = routePopPosition(
    { left: rect.left, top: rect.top, width: rect.width, bottom: rect.bottom },
    { width: pop.offsetWidth, height: pop.offsetHeight },
    { width: window.innerWidth, height: window.innerHeight },
  ); // pc-card.js
  pop.style.left = spot.left + "px";
  pop.style.top = spot.top + "px";
}
// returnFocus = 닫은 뒤 [길찾기] 버튼으로 포커스 (× · Esc · 버튼 다시 누름). 카드를 넘기거나 다시 그려 닫을 땐 false
function closeRoutePop(returnFocus) {
  if (!routePopEvent) return;
  var button = routePopButton;
  routePopEvent = null;
  routePopButton = null;
  document.getElementById("routePop").hidden = true;
  if (button.isConnected) {
    button.removeAttribute("aria-expanded"); // 모바일(하단 시트) 버튼엔 없음 — PC 창이 열린 동안만 (#241 검증)
    button.classList.remove("open");
    button.querySelector(".route-label").textContent = I18n.t("route.title");
  }
  if (returnFocus) dialogFocus.closed("routePop");
  else dialogFocus.forget("routePop");
}
document.getElementById("routePopClose").addEventListener("click", function () {
  closeRoutePop(true);
});
document.getElementById("routePopCopy").addEventListener("click", function () {
  if (!routePopEvent) return;
  copyText(extractAddress(routePopEvent.info) || "").then(
    function () {
      showToast(I18n.t("toast.addrCopied"));
    },
    function () {
      showToast(I18n.t("toast.copyFailedSelect"));
    },
  );
});
// 창 바깥을 누르면 닫힘 (창을 연 [길찾기] 버튼은 버튼이 직접 열고 닫음)
document.addEventListener("click", function (e) {
  if (!routePopEvent) return;
  if (document.getElementById("routePop").contains(e.target) || routePopButton.contains(e.target)) return;
  closeRoutePop(false);
});

// 카카오맵 웹 링크(/link/to/)는 자동차로만 열림 → 폰에서는 앱 주소(kakaomap://route, 대중교통)를 먼저 열고,
// 앱이 없어 화면이 그대로면 2.5초 뒤 웹 링크로 넘어감. PC 는 앱이 없으니 웹 링크 그대로(새 탭)
var isPhone = isAppleMobile || /Android/i.test(navigator.userAgent);
document.getElementById("routeKakao").addEventListener("click", function (e) {
  if (!routeEvent || !isPhone) return;
  e.preventDefault();
  openAppOrWeb(kakaoRouteAppUrl(routeEvent.latitude, routeEvent.longitude), this.href);
});
var APP_OPEN_WAIT = 2500;
function openAppOrWeb(appUrl, webUrl) {
  var start = Date.now();
  var left = false; // 앱으로 넘어가면 페이지가 숨겨짐(visibilitychange · pagehide)
  function onHide() {
    if (document.hidden) left = true;
  }
  function onPageHide() {
    left = true;
  }
  document.addEventListener("visibilitychange", onHide);
  window.addEventListener("pagehide", onPageHide);
  setTimeout(function () {
    document.removeEventListener("visibilitychange", onHide);
    window.removeEventListener("pagehide", onPageHide);
    // 타이머가 제때(여유 1초 안에) 돌았을 때만 웹으로: 앱 확인창 · 앱 전환으로 페이지가 멈췄다 돌아온 경우는 이미 앱이 열림
    var onTime = Date.now() - start < APP_OPEN_WAIT + 1000;
    if (!left && !document.hidden && onTime) location.href = webUrl; // 같은 탭으로 → 돌아오면 ?event=&route=1 로 복원
  }, APP_OPEN_WAIT);
  location.href = appUrl;
}
document.getElementById("routeCloseBtn").addEventListener("click", closeRouteSheet);
document.getElementById("routeCancelBtn").addEventListener("click", closeRouteSheet);
document.getElementById("routeSheet").addEventListener("click", function (e) {
  if (e.target.id === "routeSheet") closeRouteSheet(); // 덮개를 누르면 닫힘
});
document.getElementById("routeCopyBtn").addEventListener("click", function () {
  if (!routeEvent) return;
  copyText(extractAddress(routeEvent.info) || "").then(
    function () {
      showToast(I18n.t("toast.addrCopied"));
    },
    function () {
      showToast(I18n.t("toast.copyFailedLong"));
    },
  );
});

// ----- 검색창 (모바일) -----
var searchInputEl = document.getElementById("searchInput");
searchInputEl.addEventListener("input", function () {
  // 검색하면 결과가 보이도록 목록 보기로 (P3: 절반 단계 없음. 모바일은 보통 검색 화면이 대신 열림)
  if (mobileQuery.matches && sheetState === "peek") {
    setSheetState("full");
  }
});
searchInputEl.addEventListener("keydown", function (e) {
  if (e.key === "Enter") searchInputEl.blur(); // 키보드 닫기
});

// ----- PC 목록 칸 검색 띠 (11/13 확정 시안 R4 A — docs/design/2026-11-13-pc-pending.md "② 검색 · 분류") -----
// 친 글자는 상단 바 #searchInput(PC 에선 숨김) 값으로 옮겨 필터는 하나로. ✕ = 비우고 입력칸에 포커스
// 치는 동안 300ms 쉬면 지도가 결과로: 1곳 → 그 자리 · 여러 곳 → 결과 핀이 모두 보이게 · 0개 → 그대로 (pc-search.js pcSearchMapMove)
// 검색어를 지우면 목록 · 상세는 원래대로, 지도는 그 자리에 둠
var pcSearchInput = document.getElementById("pcSearchInput");
var pcSearchClear = document.getElementById("pcSearchClear");
var pcSearchMapTimer = null;

// 상단 바 값 → 검색 띠 (찜 보기 · 진행 중 전체 등이 #searchInput 을 비운 뒤 filterMarkers 가 부름)
function syncPcSearch() {
  var value = document.getElementById("searchInput").value;
  var input = document.getElementById("pcSearchInput"); // 위 var 보다 먼저 불릴 수 있어 직접 찾음
  if (input.value !== value) input.value = value;
  document.getElementById("pcSearchClear").hidden = value === "";
  input.classList.toggle("has-value", value !== "");
}

function setPcSearch(value) {
  document.getElementById("searchInput").value = value;
  filterMarkers();
  clearTimeout(pcSearchMapTimer);
  if (!value.trim()) return; // 지우면 지도는 그 자리
  pcSearchMapTimer = setTimeout(moveMapToSearchResults, PC_SEARCH_MAP_DELAY);
}

function moveMapToSearchResults() {
  if (!map || mobileQuery.matches || !document.getElementById("searchInput").value.trim()) return;
  var move = pcSearchMapMove(
    lastSidebarItems.map(function (item) {
      return item.event;
    }),
  ); // pc-search.js
  if (move.kind === "center") {
    map.panTo(new kakao.maps.LatLng(move.lat, move.lng));
  } else if (move.kind === "bounds") {
    var bounds = new kakao.maps.LatLngBounds();
    move.points.forEach(function (p) {
      bounds.extend(new kakao.maps.LatLng(p.lat, p.lng));
    });
    map.setBounds(bounds, 60, 60, 60, 60);
  }
}

pcSearchInput.addEventListener("input", function (e) {
  mapSearchComposing = !!e.isComposing; // 조합 중 "과" → 광복도 (home-picks.js searchTypingMode)
  setPcSearch(pcSearchInput.value);
});
watchSearchComposition(pcSearchInput, setMapSearchComposing, filterMarkers);
pcSearchInput.addEventListener("keydown", function (e) {
  if (e.key === "Escape" && pcSearchInput.value) {
    e.preventDefault(); // 브라우저 기본(검색칸 비우기)과 같은 일을 지도 이동 없이
    e.stopPropagation(); // 검색어만 지우고 상세 칸 키 툴팁은 그대로 (#261 검증 ⚠️)
    setPcSearch("");
  }
});
pcSearchClear.addEventListener("click", function () {
  setPcSearch("");
  pcSearchInput.focus();
});

// ----- ☰ 메뉴 열기/닫기 (PC · 모바일 공통, P4) -----
// 펼친 판: 모바일 = 로그인 · 메뉴 줄 · 화면 모드 전체(#topbarActions) / PC = 메뉴 줄만(#menuLinks)
function menuPanel() {
  return document.getElementById(mobileQuery.matches ? "topbarActions" : "menuLinks");
}
function setMenuOpen(open) {
  document.getElementById("topbar").classList.toggle("menu-open", open);
  document.getElementById("mobileMenuBtn").setAttribute("aria-expanded", open ? "true" : "false");
}
document
  .getElementById("mobileMenuBtn")
  .addEventListener("click", function (e) {
    e.stopPropagation();
    var open = !document.getElementById("topbar").classList.contains("menu-open");
    setMenuOpen(open);
    if (open) {
      // 키보드로 열어도 바로 메뉴 안을 돌 수 있게 첫 줄로 초점 (메뉴 줄이 HTML 에서 ☰ 보다 앞이라)
      var first = Array.prototype.find.call(menuPanel().querySelectorAll("a, button"), function (el) {
        return el.offsetParent !== null; // 숨긴 단추(로그인 전 로그아웃 · PC ☾ 등) 건너뜀
      });
      if (first) first.focus();
    }
  });
// 초점이 메뉴 · ☰ 밖으로 나가면 닫음 (Tab 으로 지나쳐 가도 열린 채 남지 않게)
document.addEventListener("focusin", function (e) {
  if (
    document.getElementById("topbar").classList.contains("menu-open") &&
    !menuPanel().contains(e.target) &&
    e.target !== document.getElementById("mobileMenuBtn")
  ) {
    setMenuOpen(false);
  }
});
document.addEventListener("click", function (e) {
  if (
    document.getElementById("topbar").classList.contains("menu-open") &&
    !menuPanel().contains(e.target)
  ) {
    setMenuOpen(false);
  }
});
document.addEventListener("keydown", function (e) {
  if (e.key === "Escape" && document.getElementById("topbar").classList.contains("menu-open")) {
    setMenuOpen(false);
    document.getElementById("mobileMenuBtn").focus();
  }
});

// ----- 화면 크기 변경 (회전, PC 창 크기 조절) 대응 -----
var menuLayoutMobile = mobileQuery.matches; // ☰ 판 모양이 PC · 모바일에서 달라서, 바뀌면 닫음
function applyLayout() {
  closeRoutePop(false); // 창 크기가 바뀌면 버튼 자리도 바뀜 (모바일로 넘어가면 PC 창은 없음)
  if (mobileQuery.matches !== menuLayoutMobile) {
    menuLayoutMobile = mobileQuery.matches;
    setMenuOpen(false);
    // 모바일 상세 창을 연 채 PC 로 넓히면 그 행사를 상세 칸에 (11/13 Q4 — PC 는 상세 창이 없음)
    var openEvent = !mobileQuery.matches && detailEvent ? detailEvent : null;
    if (openEvent) {
      closeDetailModal();
      pcDetailId = openEvent.id;
    }
    if (!mobileQuery.matches) hidePreview(); // 카드 줄은 모바일만
    filterMarkers(); // 목록 줄(PC 롤링 포커스) · 카드 줄 · 상세 칸을 그 화면에 맞게 다시
  }
  if (mobileQuery.matches) {
    setSheetState(sheetState);
  } else {
    // PC로 돌아가면 모바일용 위치·상태를 모두 원래대로 (하단 카드 줄은 모바일만 — PC 는 상세 칸, 11/13 Q4)
    var sidebar = document.getElementById("sidebar");
    sidebar.style.transform = "";
    sidebar.style.transition = "";
    document.getElementById("list").style.paddingBottom = "";
    document.body.classList.remove("sheet-full");
    setMenuOpen(false);
  }
  if (map) map.relayout(); // 카카오맵은 크기가 바뀌면 다시 계산시켜야 함
}

var resizeTimer = null;
window.addEventListener("resize", function () {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(applyLayout, 150);
});

// 모바일 첫 화면 = 홈 (11/4 결정). 공유 링크(?event=) · 지도 앱 복귀(&route=1)는 지금처럼 지도 위 상세부터
renderHomeLoading();
// ?event= 로 복원하면 앞 문서가 쌓은 기록 칸을 이어받음 (지도 앱에서 돌아와 다시 열린 경우), 아니면 비우고 시작
nav.start(!!initialParams.get("event"));
if (mobileQuery.matches && !initialParams.get("event")) showHome();
applyLayout();
renderPcDetail(); // PC 상세 칸: 행사를 받기 전엔 불러오는 중
