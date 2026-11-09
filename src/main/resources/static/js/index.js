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

// CSP(script-src)는 HTML 안의 onclick · onerror 를 막음 → innerHTML 로 만든 요소는 아래 두 함수로 직접 연결
// 지도 말풍선의 행사 줄(data-open-detail="행사 id") 클릭 → 상세
function bindOpenDetail(root) {
  root.querySelectorAll("[data-open-detail]").forEach(function (el) {
    el.addEventListener("click", function () {
      openDetailFromMarker(Number(el.dataset.openDetail));
    });
  });
}

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

// PC 팝업에서 찜한 행사 앞에 붙는 ★
function favoriteMark(eventId) {
  return favoriteEventIds.has(eventId)
    ? '<span class="iw-fav">★ </span>'
    : "";
}

var map;
var markerList = [];
var eventsLoaded = false; // 행사 목록을 받기 전엔 목록(스켈레톤 · 실패 안내)을 다시 그리지 않음
var currentInfowindow = null;

// ===== 모바일 레이아웃 상태값 (아래 '모바일 레이아웃' 영역에서 사용) =====
var mobileQuery = window.matchMedia("(max-width: 1024px)");
var initialParams = new URLSearchParams(location.search); // 처음 들어온 주소의 ?event= · route=

// 열린 창을 주소에 기록 (?event=번호[&route=1]) → 지도 앱 · 카카오톡에 갔다 와서 페이지가 다시 열려도 복원
function currentQuery() {
  var id = detailEvent ? detailEvent.id : routeEvent ? routeEvent.id : null;
  return id ? "?event=" + id + (routeEvent ? "&route=1" : "") : "";
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
var sheetState = "peek"; // "peek"(살짝 — 결과 바만) | "half"(절반) | "full"(전체)
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

    // (모바일 미리보기 카드용) 같은 장소 행사들을 시작일 순으로 번호 매기기 → '같은 장소 1/3'
    var groupByDate = group.slice().sort(compareStartDate);
    groupByDate.forEach(function (item, i) {
      item.group = groupByDate;
      item.groupIndex = i;
      item.groupSize = groupByDate.length;
    });

    var infowindowContent;
    if (group.length === 1) {
      var singlePlaceName = eventPlace(first);
      infowindowContent =
        '<div class="iw-single">' +
        (singlePlaceName
          ? '<div class="iw-place">' +
            escapeHtml(singlePlaceName) +
            "</div>"
          : "") +
        '<div class="iw-title" data-open-detail="' +
        first.id +
        '">' +
        favoriteMark(first.id) +
        escapeHtml(first.title) +
        "</div>" +
        "</div>";
    } else {
      // 공연일(startDate) 빠른 순으로 정렬하되, 제일 빠른 게 목록 맨 아래(스택 아래쪽)에 오도록 뒤집음
      var sorted = group.slice().sort(function (a, b) {
        var da = a.event.startDate;
        var db = b.event.startDate;
        if (!da && !db) return 0;
        if (!da) return 1;
        if (!db) return -1;
        return da.localeCompare(db);
      });
      sorted.reverse();

      var rows = sorted
        .map(function (item, i) {
          // 위에서부터 순서대로 흰색/회색 번갈아 표시 (첫 줄은 항상 흰색이라 헤더랑 안 붙음)
          return (
            '<div class="iw-row' +
            (i % 2 === 1 ? " stripe" : "") +
            '" data-open-detail="' +
            item.event.id +
            '">' +
            favoriteMark(item.event.id) +
            escapeHtml(item.event.title) +
            "</div>"
          );
        })
        .join("");
      var placeName = eventPlace(first) || first.title;
      infowindowContent =
        '<div class="iw-group">' +
        '<div class="iw-place">' +
        escapeHtml(placeName) +
        "</div>" +
        rows +
        "</div>";
    }

    // 문자열 대신 요소로 넘겨야 클릭을 연결할 수 있음 (말풍선 모양은 같음)
    var infowindowBox = document.createElement("div");
    infowindowBox.innerHTML = infowindowContent;
    bindOpenDetail(infowindowBox);
    var infowindow = new kakao.maps.InfoWindow({ content: infowindowBox.firstChild });
    kakao.maps.event.addListener(marker, "click", function () {
      if (mobileQuery.matches) {
        showPreview(groupByDate[0].event.id); // 모바일: 하단 미리보기 카드 (같은 장소면 첫 번째부터)
      } else {
        toggleInfowindow(infowindow, marker); // PC: 기존 말풍선/목록 팝업
      }
    });

    var locMarker = {
      marker: marker,
      infowindow: infowindow,
      key: first.latitude + "," + first.longitude,
      normalImage: markerImage,
      selectedImage: selectedImage,
    };
    locationMarkers.push(locMarker);
    group.forEach(function (item) {
      item.locMarker = locMarker; // 행사 → 그 행사가 있는 장소 마커
    });
  });
}

// 시작일 오름차순 비교 (날짜 없는 행사는 뒤로)
function compareStartDate(a, b) {
  var da = a.event.startDate;
  var db = b.event.startDate;
  if (!da && !db) return 0;
  if (!da) return 1;
  if (!db) return -1;
  return da.localeCompare(db);
}

// 지도를 처음 열었을 때의 기본 위치: 정조테마공연장 (팔달구 정조로 817)
// 좌표는 재단 API(XML)가 정조테마공연장 행사에 주는 값과 같게 맞춤 → 기본 위치가 그 장소 마커와 정확히 겹침
var DEFAULT_CENTER = { lat: 37.2810212008746, lng: 127.016215843564 };

kakao.maps.load(function () {
  var mapContainer = document.getElementById("map");
  var mapOption = {
    center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: 6,
  };
  map = new kakao.maps.Map(mapContainer, mapOption);

  // (모바일) 지도 빈 곳을 탭하면 미리보기 카드 닫기
  kakao.maps.event.addListener(map, "click", function () {
    hidePreview();
  });

  loadEvents();
});

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
  network: "행사를 불러오지 못했어요.<br>네트워크를 확인한 뒤 다시 시도해 주세요.",
  render: "행사를 화면에 표시하지 못했어요.<br>잠시 뒤 다시 시도해 주세요.",
};

function renderLoadError(kind) {
  var list = document.getElementById("list");
  list.removeAttribute("aria-busy");
  list.innerHTML =
    '<div class="load-error" role="alert" data-kind="' + kind + '"><span>' + LOAD_ERROR_TEXT[kind] + "</span>" +
    '<button type="button" id="retryLoadBtn">다시 시도</button></div>';
  document.getElementById("retryLoadBtn").addEventListener("click", loadEvents);
  document.getElementById("resultCount").textContent = "불러오지 못했어요";
  updateSheetHeaderLabel();
}

var eventsLoadFailed = false; // 마지막 불러오기가 실패했나 (검색 화면 안내용)

function loadEvents() {
  eventsLoaded = false;
  eventsLoadFailed = false;
  renderSkeleton();
  document.getElementById("resultCount").textContent = "행사 불러오는 중…";
  updateSheetHeaderLabel(); // 다시 시도 때 "불러오지 못했어요" 라벨이 남지 않게
  renderHomeLoading();
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

function toggleInfowindow(infowindow, marker) {
  if (currentInfowindow === infowindow) {
    infowindow.close();
    currentInfowindow = null;
    return;
  }
  if (currentInfowindow) {
    currentInfowindow.close();
  }
  infowindow.open(map, marker);
  currentInfowindow = infowindow;
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
      option.textContent = category;
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

// 구 필터 (null = 전체). 홈 "내 주변"의 "지도에서 보기"가 켜고, 지도 칩 줄 맨 앞 "📍 ○○구 ✕" 칩으로 끔
var districtFilter = null;

function setDistrictFilter(district) {
  districtFilter = district || null;
  filterMarkers();
}

// ===== 필터링 (검색어 + 카테고리 + 진행 날짜 + 구 + 지난 행사 여부, '찜'은 즐겨찾기 목록으로 필터) =====
function filterMarkers() {
  if (!eventsLoaded) return; // 아직 받는 중(스켈레톤) 또는 실패 안내 표시 중 — 로그인 확인 등이 지우지 않게
  var keyword = document.getElementById("searchInput").value.trim();
  var selectedCategory = document.getElementById("categorySelect").value;
  var showPast = document.getElementById("showPastCheckbox").checked;
  var runDays = filterDays(document.getElementById("dateFilter").value); // 한국 시간 기준, 필터 없으면 null

  updateChipActive(); // 모바일 칩 선택 표시 동기화
  updateTabBar(); // 모바일 하단 탭 선택 표시 동기화

  if (currentInfowindow) {
    currentInfowindow.close();
    currentInfowindow = null;
  }

  var visibleItems = [];
  var favScreen = onFavScreen();

  markerList.forEach(function (item) {
    if (favScreen) {
      // 찜 탭(모바일) = 따로 된 화면: 지도 쪽 조건(검색어 · 분류 · 날짜 · 구 · 지난 행사)과 상관없이 찜 전부 (종료는 배지)
      item.runMatch = null;
      if (favoriteEventIds.has(item.event.id)) visibleItems.push(item);
      return;
    }
    var matchesKeyword = matchesSearch(item.event, keyword); // 띄어쓰기 · 대소문자 무시, 제목 + 장소 (home-picks.js)
    var matchesCategory;

    if (selectedCategory === "all") {
      matchesCategory = true;
    } else if (selectedCategory === "찜") {
      matchesCategory = favoriteEventIds.has(item.event.id);
    } else {
      matchesCategory = item.event.category === selectedCategory;
    }

    var matchesPast = showPast || !isPastEvent(item.event);

    item.runMatch = runDays ? matchRunDays(item.event, runDays) : null;
    var matchesDate = item.runMatch !== "no";

    var visible =
      matchesKeyword && matchesCategory && matchesPast && matchesDate && matchesDistrict(item.event, districtFilter);

    if (visible) {
      visibleItems.push(item);
    }
  });

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
  showToast("최근 본 기록을 지웠어요");
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
    result.push("요금: " + fee);
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

// ===== Side-bar 카드 렌더링 (이미지 + 제목 + info 요약) =====
function renderSidebar(items) {
  var recent = showingRecent(); // 최근 본: 본 순서 그대로, 구분 제목 없음
  // 찜한 행사는 맨 위에 이름순, 나머지는 시작일 순
  var favorites = recent
    ? []
    : items
        .filter(function (item) {
          return favoriteEventIds.has(item.event.id);
        })
        .sort(function (a, b) {
          return a.event.title.localeCompare(b.event.title, "ko");
        });
  var others = recent
    ? items
    : items
        .filter(function (item) {
          return !favoriteEventIds.has(item.event.id);
        })
        .sort(compareStartDate);
  var sorted = favorites.concat(others);

  var list = document.getElementById("list");
  list.innerHTML = "";
  list.removeAttribute("aria-busy"); // 스켈레톤 끝
  if (recent) {
    if (sorted.length === 0) {
      renderEmptyBox(list, recentEmptyState(), EMPTY_CLOCK_SVG);
    } else {
      var note = document.createElement("div");
      note.className = "recent-note";
      note.innerHTML = '<span>이 기기에만 저장돼요</span><button type="button">모두 지우기</button>';
      note.querySelector("button").addEventListener("click", clearRecentViews);
      list.appendChild(note);
    }
  } else if (sorted.length === 0 && (onFavScreen() || document.getElementById("categorySelect").value === "찜")) {
    renderFavoriteEmpty(list);
  } else if (!isLoggedIn && (onFavScreen() || document.getElementById("categorySelect").value === "찜")) {
    // 비회원 찜 목록 맨 위: 이 기기에만 있다는 안내 + 로그인 (최근 본 안내와 같은 모양)
    var guestNote = document.createElement("div");
    guestNote.className = "recent-note guest-note";
    guestNote.innerHTML = '<span>이 기기에만 저장돼요 · 로그인하면 계정에 합쳐져요</span><button type="button">로그인</button>';
    guestNote.querySelector("button").addEventListener("click", goToLogin);
    list.appendChild(guestNote);
  }

  // 찜한 행사가 있을 때만 구분 제목 표시
  function addSectionTitle(text) {
    var h = document.createElement("div");
    h.className = "list-section-title";
    h.textContent = text;
    list.appendChild(h);
  }
  if (favorites.length > 0) addSectionTitle("★ 찜한 행사 " + favorites.length + "개 (이름순)");

  sorted.forEach(function (item, index) {
    if (favorites.length > 0 && index === favorites.length) {
      addSectionTitle("전체 행사 (날짜순)");
    }
    var div = document.createElement("div");

    var dateText = cardDateText(item);

    var imgSrc = cardImageUrl(item.event); // 사진이 없으면(인스타 행사 등) null → 사진 칸 없이
    div.className = "event-item" + (imgSrc ? "" : " no-photo");
    var infoText = item.event.info
      ? extractSummaryInfo(item.event.info, item.event.fee) // 사이드바엔 요금/장소/시간만
      : cardFallbackInfo(item.event); // info 원문이 없으면 장소 · 시간 칸
    var isFavorited = favoriteEventIds.has(item.event.id); // 찜 여부 확인

    div.innerHTML =
      (imgSrc
        ? '<img class="event-thumb" src="' + escapeHtml(imgSrc) + '" loading="lazy">'
        : "") +
      (isFavorited ? '<span class="favorite-badge">★</span>' : "") + // 찜한 행사만 별 표시
      favButtonHtml(isFavorited) + // 모바일: 사진 위 ♥ 버튼
      '<div class="event-body">' +
      '<div class="date">' +
      escapeHtml(dateText) +
      "</div>" +
      '<div class="title">' +
      escapeHtml(item.event.title) +
      "</div>" +
      badgeRowHtml(item.event) + // 모바일: 상태 · D-day (CSS order 로 제목 위에 놓임)
      '<div class="info">' +
      escapeHtml(infoText) +
      "</div>" +
      "</div>";
    var thumb = div.querySelector(".event-thumb");
    if (thumb) {
      // 사진을 못 불러오면 빈 칸을 남기지 않고 사진 없는 카드로 바꿈
      thumb.addEventListener("error", function () {
        thumb.remove();
        div.classList.add("no-photo");
      });
    }

    div.addEventListener("click", function () {
      map.setCenter(
        new kakao.maps.LatLng(item.event.latitude, item.event.longitude),
      );
      map.setLevel(5);

      // 사이드바에서 클릭해도 지도의 해당 마커를 실제로 클릭한 것처럼 InfoWindow를 띄움
      var key = item.event.latitude + "," + item.event.longitude;
      var locMarker = locationMarkers.find(function (lm) {
        return lm.key === key;
      });
      // (모바일은 상세가 전체 화면이라 지도 팝업은 띄우지 않음)
      if (locMarker && !mobileQuery.matches) {
        toggleInfowindow(locMarker.infowindow, locMarker.marker);
      }

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
    (recent ? "최근 본 " : onFavScreen() ? "찜 " : "검색 결과 ") + "<strong>" + sorted.length + "개</strong>";
  updateSheetHeaderLabel();
  lastSortedItems = sorted;
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
}

document
  .getElementById("searchInput")
  .addEventListener("input", filterMarkers);
document
  .getElementById("categorySelect")
  .addEventListener("change", filterMarkers);
document
  .getElementById("showPastCheckbox")
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
      if (text) showToast(text, "보기", showFavoriteList);
    })
    .catch(function (err) {
      console.error("비회원 찜 합치기 실패", err);
    });
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

      if (isLoggedIn) {
        status.textContent =
          "로그인됨 (회원 번호: " + data.memberId + ")";
        loginPageBtn.style.display = "none";
        mypageBtn.style.display = "inline-block";
        logoutBtn.style.display = "inline-block";
        // 로그인 상태면 이 기기 찜을 먼저 합친 뒤 즐겨찾기 목록을 받아옴
        return mergeGuestFavorites().then(function () {
          return fetch("/api/favorites").then((r) => r.json());
        });
      } else {
        status.textContent = "로그인 안 됨";
        loginPageBtn.style.display = "inline-block";
        mypageBtn.style.display = "none";
        logoutBtn.style.display = "none";
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
      filterMarkers(); // ← 즐겨찾기 목록 다 받아온 뒤, 화면도 한 번 더 새로 그림
    });
}

document
  .getElementById("logoutBtn")
  .addEventListener("click", function () {
    fetch("/api/auth/logout", { method: "POST" }).then(function () {
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
  }, 300);
});

// 아이폰 · 아이패드 (아이패드는 PC 사파리처럼 "Macintosh" 로 보고하므로 터치 여부로 구분)
var isAppleMobile =
  /iPhone|iPad|iPod/.test(navigator.userAgent) ||
  (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

// 상세 사진 칸 보이기 · 숨기기. 숨기면 PC 의 흰 × 가 안 보이므로 카드에 no-image 를 붙여 색을 바꿈
function showModalImage(visible) {
  document.getElementById("modalImage").hidden = !visible;
  document.querySelector("#detailModal .modal-card").classList.toggle("no-image", !visible);
}
document.getElementById("modalImage").addEventListener("error", function () {
  if (this.getAttribute("src")) showModalImage(false); // src="" 로 비울 때 나는 error 는 무시
});

// ===== 상세정보 큰 팝업창 열기/닫기 =====
function openDetailModal(event) {
  // 사진이 없으면(인스타 행사) 회색 빈 칸 대신 사진 칸을 숨김. 불러오다 실패해도 숨김 (아래 error 처리)
  document.getElementById("modalImage").src = event.imageUrl || "";
  showModalImage(!!event.imageUrl);
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
  scheduleEl.style.display = scheduleText ? "block" : "none";
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
  // 진행 날짜가 정해진 여러 날 행사는 고른 날 1회 일정 (아니면 지금처럼 기간 전체 종일)
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

  // 모바일은 페이지 전체가 스크롤됨 → 닫을 때 돌아갈 목록 위치를 기억하고 맨 위에서 시작
  if (!document.body.classList.contains("detail-open")) detailReturnScrollY = window.scrollY;
  document.getElementById("detailModal").style.display = "flex";
  document.body.classList.add("detail-open");
  var card = document.querySelector("#detailModal .modal-card");
  card.scrollTop = 0;
  if (mobileQuery.matches) window.scrollTo(0, 0);
  selectDetailTab("detailRows", false);
  renderDetailMap(event); // 지도는 창이 보인 뒤에 그려야 크기를 잡음
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
    [event.category, district].filter(Boolean).join(" · ");

  // 정보 표: 기간 · 일정 · 시간 · 장소 · 요금 · 문의 · 주최 (값이 있는 줄만)
  // 요금 · 문의 · 주최는 서버가 분해한 칸(V7), 동기화 전 옛 행이면 원문에서 라벨 포함 여부로 찾음
  var fields = detailFields(event);
  var rows = [];
  if (event.startDate) rows.push(["기간", document.getElementById("modalDate").textContent]);
  var schedule = formatScheduleDetail(event);
  if (schedule) rows.push(["진행", schedule]); // 값 안에 "일정 원문:" 이 있어 라벨은 "진행"
  var time = findInfo(lines, ["시간", "요일"]) || fallbackTime(event); // info 가 없으면 timeText
  if (time) rows.push(["시간", time]);
  var place = eventPlace(event);
  if (place) rows.push(["장소", place]);
  var fee = fields.fee || findInfo(lines, ["이용료", "요금", "입장료", "관람료", "참가비"], ["주차"]); // "주차요금" 은 제외
  if (fee) rows.push(["요금", fee]);
  var contact = fields.contact || findInfo(lines, ["문의"]);
  if (contact) rows.push(["문의", contact]);
  var host = fields.host || findInfo(lines, ["주최", "주관"]);
  if (host) rows.push(["주최", host]);
  var source = sourceLabel(event); // 인스타 행사만 "인스타그램 @계정"
  if (source) rows.push(["출처", source]);
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
  more.textContent = "더보기";
  more.hidden = true; // 창이 보인 뒤 measureIntro 가 3줄을 넘을 때만 다시 켬
  document.getElementById("detailIntro").hidden = !event.info;

  // 위치: 주소 (없으면 장소명), 복사 버튼은 복사할 글이 있을 때만
  var address = extractAddress(event.info) || place || "";
  document.getElementById("detailAddress").textContent = address || "위치 정보 없음";
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
  this.textContent = expanded ? "접기" : "더보기";
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
      showToast("주소를 복사했어요");
    },
    function () {
      showToast("복사하지 못했어요. 주소를 길게 눌러 복사해 주세요");
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
      showToast("링크를 복사했어요");
    },
    function () {
      showToast("복사하지 못했어요");
    },
  );
});
// 카카오톡 공유: 사진이 있으면 피드(사진 + 제목 + 설명), 없으면 글 형식. SDK 를 못 불러왔거나(차단 · 오프라인)
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
    showToast("카카오톡 공유를 불러오지 못했어요. 링크 복사를 이용해 주세요");
    return;
  }
  var event = detailEvent;
  var url = eventShareUrl(event);
  var link = { mobileWebUrl: url, webUrl: url };
  var place = eventPlace(event);
  var description = [document.getElementById("modalDate").textContent, place].filter(Boolean).join(" · ");
  try {
    if (event.imageUrl) {
      Kakao.Share.sendDefault({
        objectType: "feed",
        content: {
          title: event.title,
          description: description,
          imageUrl: new URL(event.imageUrl, location.href).href, // 상대 주소여도 절대 주소로
          link: link,
        },
        buttons: [{ title: "지도에서 보기", link: link }],
      });
    } else {
      Kakao.Share.sendDefault({
        objectType: "text",
        text: (event.title + "\n" + description).substring(0, 200),
        link: link,
      });
    }
    // 카카오톡으로 넘어가는 중 → 기록 칸은 바로 되돌리지 않음 (go(-1) 이 그 이동을 끊을 수 있음). 돌아오면 맞춤
    nav.forget("share");
    closeShareSheet();
  } catch (err) {
    console.error("카카오톡 공유 실패", err);
    showToast("카카오톡 공유에 실패했어요. 링크 복사를 이용해 주세요");
  }
});

// ===== 마커 InfoWindow 제목 클릭 → 상세 팝업 열기 =====
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
  if (wasOpen && mobileQuery.matches) window.scrollTo(0, detailReturnScrollY); // 보던 목록 위치로
  if (wasOpen && homeOpen) drawNearbyMap(); // 상세가 덮은 동안 화면이 돌았으면 작은 지도 크기 다시 맞춤
  detailEvent = null;
  closeShareSheet();
  dialogFocus.closed("detail"); // 보던 목록 위치로 스크롤한 뒤 (포커스가 화면을 끌고 가지 않게)
  nav.close("detail");
  syncUrlState();
}

// ===== ESC 키로 상세 모달 + 마커 팝업(InfoWindow) 닫기 =====
document.addEventListener("keydown", function (e) {
  if (e.key === "Escape") {
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
    if (currentInfowindow) {
      currentInfowindow.close();
      currentInfowindow = null;
    }
  }
});

// ===== 즐겨찾기 버튼 상태/토글 =====
function updateFavoriteButton(eventId) {
  // 상세창 위쪽 '★ 찜한 행사' 표시
  document.getElementById("modalFavBadge").hidden = !favoriteEventIds.has(eventId);
  var btn = document.getElementById("modalFavoriteBtn");
  var favorited = favoriteEventIds.has(eventId);
  if (favorited) {
    btn.textContent = "★ 즐겨찾기됨";
    btn.classList.add("active");
  } else {
    btn.textContent = "☆ 즐겨찾기";
    btn.classList.remove("active");
  }
  // 모바일 상세: 상단 · 하단 ♡ (채운 하트 + accent)
  ["detailHeadFav", "detailFavBtn"].forEach(function (id) {
    var heart = document.getElementById(id);
    heart.classList.toggle("active", favorited);
    heart.setAttribute("aria-pressed", favorited ? "true" : "false");
    heart.setAttribute("aria-label", favorited ? "찜 해제" : "찜하기");
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
    showToast(guestToast.text, guestToast.actionLabel, showFavoriteList);
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
    '" aria-label="찜">' +
    (isFavorited ? "♥" : "♡") +
    "</button>"
  );
}

// ----- 카테고리 칩 (드롭다운 대신 가로로 넘기는 버튼) -----
function chipLabel(value) {
  if (value === "all") return "전체";
  if (value === "찜") return "♥ 찜";
  var style = CATEGORY_STYLE[value];
  return style ? style.emoji + " " + value : value;
}

function renderCategoryChips() {
  var select = document.getElementById("categorySelect");
  var pastCheckbox = document.getElementById("showPastCheckbox");
  var chips = document.getElementById("categoryChips");
  chips.innerHTML = "";

  // 드롭다운 옵션과 같은 순서로 칩 생성 → 누르면 드롭다운 값을 바꾸고 필터 실행
  Array.from(select.options).forEach(function (option) {
    var chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.dataset.value = option.value;
    chip.textContent = chipLabel(option.value);
    chip.addEventListener("click", function () {
      select.value = option.value;
      filterMarkers();
    });
    chips.appendChild(chip);
  });

  // 날짜 칩 3개: 누르면 그 날짜 필터, 한 번 더 누르면 해제 (카테고리 칩과 함께 쓸 수 있음)
  var dateSelect = document.getElementById("dateFilter");
  [
    { value: "today", label: "📅 오늘 진행" },
    { value: "weekend", label: "📅 이번 주말" },
    { value: "week", label: "📅 7일 안" },
  ].forEach(function (option, index) {
    var chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.dataset.date = option.value;
    chip.textContent = option.label;
    chip.addEventListener("click", function () {
      dateSelect.value = dateSelect.value === option.value ? "all" : option.value;
      filterMarkers();
    });
    chips.insertBefore(chip, chips.children[index] || null); // 맨 앞에 (가로 스크롤 뒤에 숨지 않게)
  });

  // '지난 행사' 칩은 기존 체크박스를 대신 눌러줌
  var pastChip = document.createElement("button");
  pastChip.type = "button";
  pastChip.className = "chip";
  pastChip.dataset.value = "__past";
  pastChip.textContent = "🕘 지난 행사";
  pastChip.addEventListener("click", function () {
    pastCheckbox.checked = !pastCheckbox.checked;
    filterMarkers();
  });
  chips.appendChild(pastChip);

  updateChipActive();
}

function updateChipActive() {
  var selected = document.getElementById("categorySelect").value;
  var showPast = document.getElementById("showPastCheckbox").checked;
  var dateFilter = document.getElementById("dateFilter").value;
  document
    .querySelectorAll("#categoryChips .chip")
    .forEach(function (chip) {
      var active = chip.dataset.date
        ? chip.dataset.date === dateFilter
        : chip.dataset.value === "__past"
          ? showPast
          : chip.dataset.value === selected;
      chip.classList.toggle("active", active);
    });
  updateDistrictChip();
}

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
  chip.textContent = "📍 " + districtFilter + " ✕";
  chip.setAttribute("aria-label", districtFilter + " 필터 해제");
}

// ----- 바텀시트 (사이드바 목록을 아래에서 끌어올리는 방식) -----
// 상태별 시트 윗변 위치(px)를 화면 크기에 맞춰 계산
function getSheetPositions() {
  var appHeight = document.getElementById("app").clientHeight;
  var headerHeight = document.getElementById("sheetHeader").offsetHeight;
  var topbarHeight = document.getElementById("topbar").offsetHeight;
  return {
    full: topbarHeight, // 검색창 바로 아래까지
    half: Math.round(appHeight * 0.5),
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
// 켜고 끌 때 보던 목록 위치를 목록 상자 ↔ 페이지 사이로 옮김 (절반 ↔ 전체 전환에도 위치 유지)
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
  document.getElementById("sheetToggleText").textContent = state === "peek" ? "목록 보기" : "접기";
  updateSheetHeaderLabel();
  if (state !== "peek") hidePreview(); // 목록이 올라오면 하단 카드는 닫음
  updateTabBar();
}

// 결과 바(버튼)의 보조기기용 이름 · 열림 상태
function updateSheetHeaderLabel() {
  var header = document.getElementById("sheetHeader");
  var expanded = sheetState !== "peek";
  header.setAttribute("aria-expanded", expanded ? "true" : "false");
  header.setAttribute(
    "aria-label",
    document.getElementById("resultCount").textContent + ", " + (expanded ? "접기" : "목록 보기"),
  );
}

function nearestSheetState(y) {
  var pos = getSheetPositions();
  return ["full", "half", "peek"].reduce(function (best, s) {
    return Math.abs(pos[s] - y) < Math.abs(pos[best] - y) ? s : best;
  }, "peek");
}

// 손잡이 부분을 끌어서 올리고 내리기 (탭하면 살짝 ↔ 절반)
(function setupSheetDrag() {
  var header = document.getElementById("sheetHeader");
  var ORDER = ["full", "half", "peek"];
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
      setSheetState(sheetState === "peek" ? "half" : "peek");
      return;
    }

    var from = ORDER.indexOf(nearestSheetState(startSheetY));
    var target;
    if (velocity < -0.4) {
      target = ORDER[Math.max(0, from - 1)]; // 위로 튕기면 한 단계 위
    } else if (velocity > 0.4) {
      target = ORDER[Math.min(2, from + 1)]; // 아래로 튕기면 한 단계 아래
    } else {
      target = nearestSheetState(sheetY); // 천천히 놓으면 가까운 위치로
    }
    setSheetState(target);
  }
  header.addEventListener("pointerup", endDrag);
  header.addEventListener("pointercancel", endDrag);

  // 키보드: Enter · Space 로 살짝 ↔ 절반
  header.addEventListener("keydown", function (e) {
    if (!mobileQuery.matches) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      setSheetState(sheetState === "peek" ? "half" : "peek");
    }
  });
})();

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

// ----- 홈 화면 (디자인 01): 이번 주 행사 수 + 캐러셀 + 이달의 행사 + 곧 종료 / 곧 시작 (고르는 규칙은 home-picks.js) -----
// 페이지 전체가 스크롤 (index.css body.home-open, 사파리 주소창이 줄어들게)
var homeSoonMode = "ending"; // "ending" 곧 종료 | "starting" 곧 시작

function showHome() {
  nav.close("tab"); // 홈에서 나가며 쌓은 칸을 되돌림 (뒤로 가기로 왔으면 이미 빠짐)
  favView = "fav"; // 찜 탭을 나감 → 다음엔 '찜'부터
  if (favTabOpen) {
    favTabOpen = false;
    filterMarkers(); // 찜 화면 흔적(탭 줄 · 칩 흐림 · tabpanel · "찜 N개")을 지움 → 홈 뒤 PC 폭으로 바뀌어도 남지 않게
  }
  hidePreview();
  if (sheetState !== "peek") setSheetState("peek"); // 목록 전체(페이지 스크롤)는 풀어 두고 홈으로
  homeOpen = true;
  document.body.classList.add("home-open");
  window.scrollTo(0, 0);
  carouselJump(carouselIndex); // 숨어 있을 때는 폭이 0 이라 자리를 못 맞춤 → 보일 때 맞춤
  drawNearbyMap(); // 작은 지도도 같은 이유로 보일 때 그림
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
  document.getElementById("homeWeekCount").textContent = thisWeekEvents(homeEvents()).length + "개";
  renderHomeCarousel();
  renderHomeMonth();
  renderHomeSoon();
  renderHomeNearby();
}

// ----- 포스터 캐러셀 (디자인 01): 카드 280px · 스와이프 · 끝에서 처음으로 · 진행 막대 + "1 / 5" -----
// 무한: [마지막 복제] 1 … N [첫 번째 복제] 로 놓고, 복제 카드에서 멈추면 같은 모양의 진짜 카드로 순간 이동
var carouselIndex = 0; // 지금 보이는 진짜 카드 (0부터)
var carouselCount = 0;
var carouselSettleTimer = null;

function posterSlideHtml(event, index, count, clone) {
  var status = eventStatus(event);
  var dday = ddayText(event);
  var sub = [event.category, eventDistrict(event), formatEventDates(event)].filter(Boolean);
  return (
    '<div class="poster-slide"' +
    (clone
      ? ' aria-hidden="true" inert'
      : ' role="group" aria-roledescription="슬라이드" aria-label="' + (index + 1) + " / " + count + '"') +
    ">" +
    '<button type="button" class="poster-card" data-event-id="' + event.id + '"' + (clone ? ' tabindex="-1"' : "") + ">" +
    '<span class="poster-img">' +
    '<span class="row-poster-text">' + escapeHtml(event.category || "") + "</span>" +
    '<img src="' + escapeHtml(event.imageUrl) + '" alt="" loading="' + (index === 0 && !clone ? "eager" : "lazy") + '">' +
    "</span>" +
    (status
      ? '<span class="row-badges"><span class="status-badge ' + status + '">' + statusLabel(status) + "</span>" +
        (dday ? '<span class="dday-badge">' + dday + "</span>" : "") +
        "</span>"
      : "") +
    '<span class="poster-title">' + escapeHtml(event.title) + "</span>" +
    '<span class="row-sub">' + escapeHtml(sub.join(" · ")) + "</span>" +
    "</button>" +
    "</div>"
  );
}

function renderHomeCarousel() {
  var section = document.getElementById("homeCarousel");
  var track = document.getElementById("homeCarouselTrack");
  var picks = carouselPicks(homeEvents());
  carouselCount = picks.length;
  carouselIndex = 0;
  section.hidden = picks.length === 0;
  var slides = picks.map(function (event, i) {
    return posterSlideHtml(event, i, picks.length, false);
  });
  if (picks.length > 1) {
    var last = picks.length - 1;
    slides.unshift(posterSlideHtml(picks[last], last, picks.length, true));
    slides.push(posterSlideHtml(picks[0], 0, picks.length, true));
  }
  track.innerHTML = slides.join("");
  track.querySelectorAll(".poster-card").forEach(function (card) {
    var img = card.querySelector("img");
    if (img) hideOnError(img); // 못 불러오면 회색 칸 + 분류 글자
    card.addEventListener("click", function () {
      openDetailFromMarker(Number(card.dataset.eventId));
    });
  });
  carouselJump(0);
  updateCarouselProgress();
}

// 진짜 카드 번호 → 줄 안의 칸 번호 (앞에 복제 1장이 있으면 +1)
function carouselSlot(index) {
  return carouselCount > 1 ? index + 1 : index;
}

// 그 칸이 왼쪽 여백 자리에 오도록 순간 이동 (홈이 숨어 있으면 폭이 0 → showHome 때 다시)
function carouselJump(index) {
  var track = document.getElementById("homeCarouselTrack");
  var slide = track.children[carouselSlot(index)];
  if (!slide || track.clientWidth === 0) return; // 칸이 없음(스켈레톤 · 빈 캐러셀) · 숨어 있음
  track.scrollLeft = slide.offsetLeft - track.children[0].offsetLeft;
}

// 지금 스크롤 위치에서 가장 가까운 칸 번호
function carouselNearestSlot() {
  var track = document.getElementById("homeCarouselTrack");
  var slides = track.children;
  if (slides.length < 2) return 0;
  var step = slides[1].offsetLeft - slides[0].offsetLeft;
  return Math.max(0, Math.min(slides.length - 1, Math.round(track.scrollLeft / step)));
}

function updateCarouselProgress() {
  var show = carouselCount > 1;
  document.querySelector("#homeCarousel .carousel-progress").hidden = !show;
  if (!show) return;
  document.getElementById("homeCarouselFill").style.width = ((carouselIndex + 1) / carouselCount) * 100 + "%";
  document.getElementById("homeCarouselCount").textContent = carouselIndex + 1 + " / " + carouselCount;
}

// 스와이프 중에도 번호 · 막대를 바로 갱신
function onCarouselScroll() {
  if (carouselCount < 2) return;
  carouselIndex = (carouselNearestSlot() - 1 + carouselCount) % carouselCount;
  updateCarouselProgress();
  clearTimeout(carouselSettleTimer);
  carouselSettleTimer = setTimeout(settleCarousel, 150); // scrollend 를 모르는 브라우저용
}

// 멈춘 곳이 복제 카드면 진짜 카드로 (같은 그림이라 눈에 안 띔)
function settleCarousel() {
  clearTimeout(carouselSettleTimer);
  if (carouselCount < 2) return;
  var slot = carouselNearestSlot();
  if (slot === 0 || slot === carouselCount + 1) carouselJump(carouselIndex);
}

var homeCarouselTrack = document.getElementById("homeCarouselTrack");
homeCarouselTrack.addEventListener("scroll", onCarouselScroll, { passive: true });
homeCarouselTrack.addEventListener("scrollend", settleCarousel);
window.addEventListener("resize", function () {
  if (!homeOpen) return;
  carouselJump(carouselIndex); // 화면 회전 · 폭 변화 뒤 카드 자리 맞춤
  drawNearbyMap(); // 작은 지도 크기 · 범위 다시 맞춤
});

// 행사 행 (디자인 2번 공통 부품): 포스터 56×70 + 배지 + 제목 + 보조 줄(분류 · 구 · 기간)
// 사진이 없거나 못 불러오면 회색 칸에 분류 글자. 홈 곧 종료 · 곧 시작에서 쓰고, 검색 결과(S2)에서도 같이 씀
function eventRowHtml(event) {
  var status = eventStatus(event);
  var dday = ddayText(event);
  var sub = [event.category, eventDistrict(event), formatEventDates(event)].filter(Boolean);
  return (
    '<button type="button" class="event-row" data-event-id="' + event.id + '">' +
    '<span class="row-poster">' +
    '<span class="row-poster-text">' + escapeHtml(event.category || "") + "</span>" +
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

// 행 안의 사진 실패 → 숨겨서 회색 칸 + 분류 글자가 보이게 · 행을 누르면 상세
function bindEventRows(root) {
  root.querySelectorAll(".event-row").forEach(function (row) {
    var img = row.querySelector("img");
    if (img) hideOnError(img);
    row.addEventListener("click", function () {
      openDetailFromMarker(Number(row.dataset.eventId));
    });
  });
}

function renderHomeSoon() {
  var list = document.getElementById("homeSoonList");
  list.removeAttribute("aria-busy");
  var ending = homeSoonMode === "ending";
  var picks = ending ? endingSoon(homeEvents()) : startingSoon(homeEvents());
  if (picks.length === 0) {
    list.innerHTML =
      '<p class="home-empty">' + (ending ? "곧 종료되는 행사가 없어요" : "곧 시작하는 행사가 없어요") + "</p>";
    return;
  }
  list.innerHTML = picks.map(eventRowHtml).join("");
  bindEventRows(list);
}

// ----- 이달의 행사 (디자인 01): 가로 카드 148px (포스터 + 배지 + 제목 + 기간), 끝에 "전체 보기 →" → 목록 탭 -----
function monthCardHtml(event) {
  var status = eventStatus(event);
  return (
    '<button type="button" class="month-card" data-event-id="' + event.id + '">' +
    '<span class="poster-img">' +
    '<span class="row-poster-text">' + escapeHtml(event.category || "") + "</span>" +
    (event.imageUrl ? '<img src="' + escapeHtml(event.imageUrl) + '" alt="" loading="lazy">' : "") +
    "</span>" +
    (status
      ? '<span class="row-badges"><span class="status-badge ' + status + '">' + statusLabel(status) + "</span></span>"
      : "") +
    '<span class="month-title">' + escapeHtml(event.title) + "</span>" +
    '<span class="row-sub">' + escapeHtml(formatEventDates(event) || "") + "</span>" +
    "</button>"
  );
}

function renderHomeMonth() {
  var section = document.getElementById("homeMonth");
  var track = document.getElementById("homeMonthTrack");
  var picks = monthEvents(homeEvents());
  section.hidden = picks.length === 0;
  document.getElementById("homeMonthTitle").textContent = monthTitle();
  track.innerHTML =
    picks.map(monthCardHtml).join("") +
    '<button type="button" class="month-more" id="homeMonthMore">전체 보기 →</button>';
  track.scrollLeft = 0; // 다시 그릴 때 처음 카드부터
  track.querySelectorAll(".month-card").forEach(function (card) {
    var img = card.querySelector("img");
    if (img) hideOnError(img); // 못 불러오면 회색 칸 + 분류 글자
    card.addEventListener("click", function () {
      openDetailFromMarker(Number(card.dataset.eventId));
    });
  });
  document.getElementById("homeMonthMore").addEventListener("click", function () {
    selectTab("list"); // 하단 탭 "목록"과 같음 (지금 걸린 필터 그대로)
  });
}

function renderHomeLoading() {
  document.getElementById("homeWeekRange").textContent = weekLabel(weekRange());
  document.getElementById("homeWeekCount").textContent = "…";
  var carousel = document.getElementById("homeCarousel");
  carouselCount = 0; // 다시 시도 때 이전 칸 수가 남아 스켈레톤 위에서 없는 칸을 찾지 않게
  carouselIndex = 0;
  carousel.hidden = false;
  carousel.querySelector(".carousel-progress").hidden = true;
  document.getElementById("homeMonth").hidden = true; // 불러오는 동안은 칸을 비워 둠 (캐러셀 스켈레톤만)
  document.getElementById("homeNearby").hidden = true;
  document.getElementById("homeCarouselTrack").innerHTML =
    '<div class="poster-slide" aria-hidden="true"><span class="poster-img sk"></span>' +
    '<span class="sk sk-line w40"></span><span class="sk sk-line w80"></span></div>';
  var list = document.getElementById("homeSoonList");
  list.setAttribute("aria-busy", "true");
  var row =
    '<div class="event-row" aria-hidden="true"><span class="row-poster sk"></span>' +
    '<span class="row-body"><span class="sk sk-line w40"></span><span class="sk sk-line w80"></span>' +
    '<span class="sk sk-line w60"></span></span></div>';
  list.innerHTML = row + row + row;
}

function renderHomeError() {
  document.getElementById("homeWeekCount").textContent = "-";
  document.getElementById("homeCarousel").hidden = true;
  document.getElementById("homeMonth").hidden = true;
  document.getElementById("homeNearby").hidden = true;
  var list = document.getElementById("homeSoonList");
  list.removeAttribute("aria-busy");
  list.innerHTML =
    '<div class="home-empty" role="alert"><span>행사를 불러오지 못했어요.</span>' +
    '<button type="button" id="homeRetryBtn">다시 시도</button></div>';
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

// 날짜 칩: 지도 탭으로 넘어가며 그 날짜 필터 (지도 칩과 달리 누를 때마다 켬, 해제는 지도 칩에서)
document.querySelectorAll("#homeDateChips .chip").forEach(function (chip) {
  chip.addEventListener("click", function () {
    document.getElementById("dateFilter").value = chip.dataset.date;
    districtFilter = null; // "내 주변"에서 켠 구 필터가 남아 날짜만 고른 결과가 줄지 않게
    selectTab("map"); // filterMarkers 가 지도 칩 표시까지 맞춤
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
      chip.textContent = district.replace(/구$/, ""); // 장안 · 권선 · 팔달 · 영통
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
    ? homeDistrict + " 행사 " + nearbyPicks.length + "개 · 지도에서 보기 →"
    : homeDistrict + "에는 지금 볼 수 있는 행사가 없어요";
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
  document.getElementById("showPastCheckbox").checked = false;
  districtFilter = district; // selectTab 이 filterMarkers 로 지도 · 목록 · 칩을 다시 그림
  selectTab("map");
  var top = document.getElementById("topbar").offsetHeight + 16; // 검색창 · 칩 줄 아래
  var bottom = document.getElementById("sheetHeader").offsetHeight + 16; // 결과 바 위
  fitMapToEvents(map, events, [top, 40, bottom, 40]);
}

document.getElementById("homeNearbyGo").addEventListener("click", goNearbyOnMap);

// PC 너비로 바뀌면 구 칩이 없어 끌 방법이 없음 → 구 필터를 풂
mobileQuery.addEventListener("change", function (e) {
  if (e.matches) {
    // 모바일에선 지도 검색창을 누르면 검색 화면이 열려 PC 에서 친 검색어를 지울 수 없음 → 넘어올 때 풂
    var searchInput = document.getElementById("searchInput");
    if (searchInput.value) {
      searchInput.value = "";
      filterMarkers();
    }
    return;
  }
  if (districtFilter) setDistrictFilter(null);
  closeSearch(); // 검색 화면도 모바일 전용
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
    '<span class="search-title"><strong>' + district + "</strong> · 지도에서 행사 " + count + "개</span>" +
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
      ? '<p class="search-empty" role="alert">행사를 불러오지 못했어요. 닫은 뒤 다시 시도해 주세요</p>'
      : '<p class="search-empty">행사를 불러오는 중이에요</p>';
    return;
  }
  if (!keyword.trim()) {
    list.innerHTML = '<p class="search-empty">행사 이름이나 장소로 찾아보세요</p>';
    return;
  }
  var events = homeEvents();
  var district = searchDistrict(keyword);
  var districtPicks = district ? districtEvents(events, district) : [];
  // 입력 중: 그대로 찾아 없으면 치는 중인 마지막 글자를 넓혀 찾음 ("숭" → 수원, "수워" → 수원). 엔터 결과는 그대로
  var typing = !results && searchTyping(events, keyword);
  var picks = searchEvents(events, keyword, undefined, typing); // 운영중 · 오픈예정(종료 임박순) → 날짜 없음 → 종료
  var html = searchDistrictRowHtml(district, districtPicks.length);
  if (results) {
    // 결과: 맞는 행사 전부 (입력 중 행과 같은 규칙이라 종료 행사도 맨 아래에 나옴)
    html +=
      '<div class="result-head"><span>검색 결과 <strong>' + picks.length + "개</strong></span>" +
      '<span class="result-sort">종료 임박순</span></div>';
    html += picks.length
      ? picks.map(searchResultRowHtml).join("")
      : '<div class="search-empty" role="status"><strong>‘' + escapeHtml(keyword.trim()) + "’ 검색 결과가 없어요</strong>" +
        "<span>띄어쓰기 없이 짧게 쓰거나, 장소 이름(예: 아트리움 · 행궁)으로 찾아보세요</span></div>";
  } else {
    html += picks
      .slice(0, SEARCH_SUGGEST_MAX)
      .map(function (event) {
        return searchRowHtml(event, keyword, typing);
      })
      .join("");
    if (!html) html = '<p class="search-empty">‘' + escapeHtml(keyword.trim()) + "’와 맞는 행사가 없어요</p>";
  }
  list.innerHTML = html;
  list.scrollTop = 0;
  list.querySelectorAll(".search-row[data-event-id]").forEach(function (row) {
    row.addEventListener("click", function () {
      openDetailFromMarker(Number(row.dataset.eventId));
    });
  });
  bindEventRows(list); // 결과 행: 사진 실패 → 회색 칸, 누르면 상세
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

searchViewInput.addEventListener("input", function () {
  searchMode = "suggest"; // 다시 입력하면 입력 중 화면으로
  renderSearch();
});
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

function renderPreviewStrip(sortedItems) {
  var strip = document.getElementById("previewStrip");
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
      var stacked = item.groupSize > 1;
      var sub = [];
      if (stacked) {
        sub.push('<span class="preview-stack">같은 장소 ' + (item.groupIndex + 1) + "/" + item.groupSize + "</span>");
      }
      sub.push(escapeHtml(cardDateText(item)));
      var place = eventPlace(item.event);
      if (place) sub.push(escapeHtml(place));
      var district = eventDistrict(item.event);
      if (district) sub.push(district);

      var card = document.createElement("article");
      card.className = "preview-card";
      card.dataset.eventId = item.event.id;
      card.innerHTML =
        '<img class="preview-poster" src="' +
        escapeHtml(item.event.imageUrl || "") +
        '" alt="" loading="lazy">' +
        '<div class="preview-body">' +
        badgeRowHtml(item.event) +
        '<div class="preview-title">' +
        escapeHtml(item.event.title) +
        "</div>" +
        '<div class="preview-sub">' +
        sub.join(" · ") +
        "</div>" +
        '<div class="preview-actions">' +
        '<button type="button" class="preview-detail">상세 보기</button>' +
        '<button type="button" class="preview-route">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 11 21 3l-8 18-2-8-8-2z" /></svg>길찾기</button>' +
        "</div>" +
        "</div>" +
        favButtonHtml(favoriteEventIds.has(item.event.id));
      hideOnError(card.querySelector(".preview-poster"));

      card.addEventListener("click", function () {
        openDetailModal(item.event); // 카드 어디를 눌러도 상세 (상세 보기 버튼 포함)
      });
      card.querySelector(".preview-route").addEventListener("click", function (e) {
        e.stopPropagation(); // 길찾기 창만 열고 상세는 열지 않음
        openRouteSheet(item.event);
      });
      card.querySelector(".card-fav").addEventListener("click", function (e) {
        e.stopPropagation();
        toggleFavorite(item.event.id);
      });

      strip.appendChild(card);
    });
  });
  updatePreviewLabel();
}

function findPreviewCard(eventId) {
  return document.querySelector(
    '#previewStrip [data-event-id="' + eventId + '"]',
  );
}

// "가까운 순 · 현재 / 전체"
function updatePreviewLabel() {
  var strip = document.getElementById("previewStrip");
  var cards = Array.from(strip.children);
  var index = cards.findIndex(function (card) {
    return Number(card.dataset.eventId) === previewEventId;
  });
  document.getElementById("previewLabel").textContent =
    cards.length === 0 ? "" : "가까운 순 · " + (index + 1) + " / " + cards.length;
}

// 카드가 화면 가운데 오도록 가로 스크롤
function scrollStripTo(card) {
  var strip = document.getElementById("previewStrip");
  strip.scrollLeft =
    card.offsetLeft - (strip.clientWidth - card.offsetWidth) / 2;
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
  if (sheetState !== "peek") setSheetState("peek"); // 목록이 올라와 있으면 내려서 카드가 보이게
  document.getElementById("previewArea").classList.add("show");
  scrollStripTo(card);
  selectMarker(eventId, true);
}

function hidePreview() {
  if (previewEventId === null) return;
  previewEventId = null;
  previewOrigin = null;
  document.getElementById("previewArea").classList.remove("show");
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
var stripScrollTimer = null;
document
  .getElementById("previewStrip")
  .addEventListener("scroll", function () {
    clearTimeout(stripScrollTimer);
    stripScrollTimer = setTimeout(function () {
      if (previewEventId === null) return;
      var strip = document.getElementById("previewStrip");
      var center = strip.scrollLeft + strip.clientWidth / 2;
      var closest = null;
      var closestDist = Infinity;
      Array.from(strip.children).forEach(function (card) {
        var dist = Math.abs(
          card.offsetLeft + card.offsetWidth / 2 - center,
        );
        if (dist < closestDist) {
          closestDist = dist;
          closest = card;
        }
      });
      if (!closest) return;
      var id = Number(closest.dataset.eventId);
      if (id !== previewEventId) {
        previewEventId = id;
        selectMarker(id, true);
        updatePreviewLabel();
      }
    }, 120);
  });

// ----- 토스트 (찜 · 주소 복사 · 링크 복사 안내, 2.5초 뒤 자동으로 사라짐) -----
var toastTimer = null;
function showToast(message, actionLabel, onAction) {
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
  toastDuration = actionLabel ? 4000 : 2500; // 버튼 있는 토스트는 누를 시간을 더 줌
  clearTimeout(toastTimer);
  toastTimer = setTimeout(hideToast, toastDuration);
}
var toastDuration = 2500;
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
  document.getElementById("routeAddress").textContent = address || "주소 정보 없음";
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
      showToast("주소를 복사했어요");
    },
    function () {
      showToast("복사하지 못했어요. 주소를 길게 눌러 복사해 주세요");
    },
  );
});

// ----- 검색창 (모바일) -----
var searchInputEl = document.getElementById("searchInput");
searchInputEl.addEventListener("input", function () {
  // 검색하면 결과가 보이도록 목록을 절반까지 올림
  if (mobileQuery.matches && sheetState === "peek") {
    setSheetState("half");
  }
});
searchInputEl.addEventListener("keydown", function (e) {
  if (e.key === "Enter") searchInputEl.blur(); // 키보드 닫기
});

// ----- ☰ 메뉴 열기/닫기 -----
document
  .getElementById("mobileMenuBtn")
  .addEventListener("click", function (e) {
    e.stopPropagation();
    document.getElementById("topbar").classList.toggle("menu-open");
  });
document.addEventListener("click", function (e) {
  var topbar = document.getElementById("topbar");
  if (
    topbar.classList.contains("menu-open") &&
    !document.getElementById("topbarActions").contains(e.target)
  ) {
    topbar.classList.remove("menu-open");
  }
});

// ----- 화면 크기 변경 (회전, PC 창 크기 조절) 대응 -----
function applyLayout() {
  if (mobileQuery.matches) {
    if (currentInfowindow) {
      currentInfowindow.close();
      currentInfowindow = null;
    }
    setSheetState(sheetState);
  } else {
    // PC로 돌아가면 모바일용 위치·상태를 모두 원래대로
    hidePreview();
    var sidebar = document.getElementById("sidebar");
    sidebar.style.transform = "";
    sidebar.style.transition = "";
    document.getElementById("list").style.paddingBottom = "";
    document.body.classList.remove("sheet-full");
    document.getElementById("topbar").classList.remove("menu-open");
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
