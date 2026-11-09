// 찜 알림 (화면 안 배지): 찜한 행사가 내일 시작 · 3일 안에 종료면 찜 탭 위에 알림 칸 + 하단 '찜' 탭 빨간 점
// 메일 · 푸시 없이 화면 안에서만 (11/9 사용자 선택). 회원 찜 · 비회원(기기) 찜 모두 같은 규칙
// 본 알림 표시(빨간 점 끄기)만 이 기기 브라우저 저장소에 — 서버로 보내지 않음. 시험: node --test src/test/js/ (CI 에서 실행)
var alertSchedule =
  typeof module !== "undefined"
    ? require("./schedule-format.js")
    : { eventStatus: eventStatus, daysUntil: daysUntil };

var FAV_ALERT_START_DAYS = 1; // 시작 D-1 (내일 시작)
var FAV_ALERT_END_DAYS = 3; // 종료 D-3 ~ 오늘 종료
var FAV_ALERT_SEEN_MAX = 200; // 저장해 둔 "본 알림" 최대 개수 (찜 최대 100 × 시작 · 종료)

// 행사 하나의 알림. 없으면 null
// starting = 오픈예정이고 시작까지 1일, ending = 운영중이고 종료까지 0~3일
function favoriteAlertOf(event, now) {
  var status = alertSchedule.eventStatus(event, now);
  if (status === "upcoming") {
    var toStart = alertSchedule.daysUntil(event.startDate, now);
    if (toStart > FAV_ALERT_START_DAYS) return null;
    return { event: event, kind: "starting", days: toStart, date: event.startDate };
  }
  if (status === "open") {
    var endDate = event.endDate || event.startDate;
    var toEnd = alertSchedule.daysUntil(endDate, now);
    if (toEnd > FAV_ALERT_END_DAYS) return null;
    return { event: event, kind: "ending", days: toEnd, date: endDate };
  }
  return null;
}

// 찜한 행사 중 알림이 있는 것. 남은 날 적은 순 → 같으면 종료 먼저(놓치기 쉬움) → 제목
function favoriteAlerts(events, favoriteIds, now) {
  return events
    .filter(function (event) {
      return favoriteIds.has(event.id);
    })
    .map(function (event) {
      return favoriteAlertOf(event, now);
    })
    .filter(Boolean)
    .sort(function (a, b) {
      if (a.days !== b.days) return a.days - b.days;
      if (a.kind !== b.kind) return a.kind === "ending" ? -1 : 1;
      return a.event.title.localeCompare(b.event.title, "ko");
    });
}

// 알림 배지 글: "내일 시작" · "오늘 종료" · "N일 뒤 종료"
function favoriteAlertLabel(alert) {
  if (alert.kind === "starting") return "내일 시작";
  return alert.days === 0 ? "오늘 종료" : alert.days + "일 뒤 종료";
}

// 본 알림을 가리는 열쇠: 행사 번호 + 종류 + 날짜 (동기화로 날짜가 바뀌면 새 알림)
function favoriteAlertKey(alert) {
  return alert.event.id + ":" + alert.kind + ":" + alert.date;
}

var ALERT_KEY_PATTERN = /^[1-9]\d*:(starting|ending):\d{4}-\d{2}-\d{2}$/;

// 저장해 둔 글 → 본 알림 열쇠 목록. 깨졌거나 모양이 다른 값은 뺌
function parseSeenAlertKeys(raw) {
  var list;
  try {
    list = JSON.parse(raw);
  } catch (e) {
    return [];
  }
  if (!Array.isArray(list)) return [];
  return list
    .filter(function (key) {
      return typeof key === "string" && ALERT_KEY_PATTERN.test(key);
    })
    .slice(0, FAV_ALERT_SEEN_MAX);
}

// 아직 안 본 알림 수 (하단 '찜' 탭 빨간 점)
function unseenAlertCount(alerts, seenKeys) {
  var seen = new Set(seenKeys);
  return alerts.filter(function (alert) {
    return !seen.has(favoriteAlertKey(alert));
  }).length;
}

// 알림 칸이 실제로 화면에 보일 때만 본 것으로 기록 (하단 '찜' 탭 점을 끔)
// PC = 사이드바에 늘 보임, 모바일 = 찜 탭 화면만 (지도 탭 '♥ 찜' 칩은 시트가 내려가 있을 수 있어 기록 안 함)
function shouldMarkAlertsSeen(isMobile, onFavScreen) {
  return !isMobile || onFavScreen;
}

// 본 알림 저장소. storage = window.localStorage (시험에선 가짜), 못 쓰면 null → 이 페이지에서만 메모리로
// markSeen 은 지금 알림 열쇠로 통째로 바꿈 → 지난 알림이 쌓이지 않음
function createAlertSeenStore(storage, key) {
  var memory = [];
  var usable = !!storage;

  function list() {
    if (usable) {
      try {
        var raw = storage.getItem(key);
        return raw === null ? [] : parseSeenAlertKeys(raw);
      } catch (e) {
        usable = false;
      }
    }
    return memory;
  }

  function markSeen(alerts) {
    var keys = alerts.map(favoriteAlertKey).slice(0, FAV_ALERT_SEEN_MAX);
    memory = keys;
    if (!usable) return keys;
    try {
      if (keys.length) {
        storage.setItem(key, JSON.stringify(keys));
      } else {
        storage.removeItem(key);
      }
    } catch (e) {
      usable = false;
    }
    return keys;
  }

  return { list: list, markSeen: markSeen };
}

if (typeof module !== "undefined") {
  module.exports = {
    FAV_ALERT_START_DAYS: FAV_ALERT_START_DAYS,
    FAV_ALERT_END_DAYS: FAV_ALERT_END_DAYS,
    favoriteAlertOf: favoriteAlertOf,
    favoriteAlerts: favoriteAlerts,
    favoriteAlertLabel: favoriteAlertLabel,
    favoriteAlertKey: favoriteAlertKey,
    parseSeenAlertKeys: parseSeenAlertKeys,
    unseenAlertCount: unseenAlertCount,
    shouldMarkAlertsSeen: shouldMarkAlertsSeen,
    createAlertSeenStore: createAlertSeenStore,
  };
}
