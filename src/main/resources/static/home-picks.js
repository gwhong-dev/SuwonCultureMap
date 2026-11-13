// 모바일 홈 칸별 행사 고르기 규칙 — 11/11 시안 3칸 · 내 주변 · 검색 (계획 docs/design/mobile-home-search-plan.md 2번)
// 오늘 · 상태는 schedule-format.js 와 같은 기준(한국 시간). 시험: node --test src/test/js/ (CI 에서 실행)
var homeSchedule =
  typeof module !== "undefined"
    ? require("./schedule-format.js")
    : { eventStatus: eventStatus, koreaToday: koreaToday, matchRunDays: matchRunDays };

// 홈 3칸 (11/11 시안): ① 지금 진행 중 ② 곧 종료 | 곧 시작 (7일 안) ③ 달별 행사 (다음 달부터)
// 겹침 규칙: ① 은 ② "곧 종료"에 나온 행사를 빼고 보여 줌 (같은 행사가 위아래 두 번 나오지 않게). 수 N 은 진행 중 전체 (오늘 진행하는 행사)
var HOME_SOON_DAYS = 7; // 오늘 포함 7일 = 지도 칩 "7일 안"(nextSevenDays)과 같음
var HOME_NOW_MAX = 10; // 지금 진행 중 가로 카드 수
var HOME_MONTHS_AHEAD = 11; // 달별 칩: 다음 달 ~ 11달 뒤 (12달 뒤는 이번 달과 이름이 같아 뺌)

function homeAddDays(isoDate, days) {
  var parts = isoDate.split("-");
  var day = Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  return new Date(day + days * 86400000).toISOString().slice(0, 10);
}

// 7일 안의 마지막 날 (오늘 + 6일, 한국 시간)
function soonLastDay(now) {
  return homeAddDays(homeSchedule.koreaToday(now), HOME_SOON_DAYS - 1);
}

// 같은 날이면 제목 순 (새로고침마다 순서가 바뀌지 않게)
function byDateThenTitle(dateOf) {
  return function (a, b) {
    var diff = dateOf(a).localeCompare(dateOf(b));
    return diff !== 0 ? diff : (a.title || "").localeCompare(b.title || "");
  };
}

function endDateOf(event) {
  return event.endDate || event.startDate;
}

// ② 곧 종료: 운영중이고 끝나는 날이 7일 안, 끝나는 날 가까운 순 (종료일 없는 행사 = 시작일 하루)
function endingSoon(events, now) {
  var last = soonLastDay(now);
  return events
    .filter(function (event) {
      return homeSchedule.eventStatus(event, now) === "open" && endDateOf(event) <= last;
    })
    .sort(byDateThenTitle(endDateOf));
}

// ② 곧 시작: 시작 전이고 7일 안에 시작, 시작일 가까운 순
function startingSoon(events, now) {
  var last = soonLastDay(now);
  return events
    .filter(function (event) {
      return homeSchedule.eventStatus(event, now) === "upcoming" && event.startDate <= last;
    })
    .sort(
      byDateThenTitle(function (event) {
        return event.startDate;
      }),
    );
}

// ① 지금 진행 중: 운영중이고 오늘 진행 (진행 날짜를 알면 오늘이 그중 하나, 모르면 기간 안 = 지도 날짜 칩 "진행 중"(today)과 같음)
// 수 N = "전체 →"(목록 + 날짜 칩 "진행 중") 수와 같게 matchRunDays 그대로 씀 → 시작일 없는 행사는 빠짐. 끝나는 날 가까운 순
function nowOpenEvents(events, now) {
  var today = homeSchedule.koreaToday(now);
  return events
    .filter(function (event) {
      if (homeSchedule.eventStatus(event, now) !== "open") return false;
      return homeSchedule.matchRunDays(event, [today]) !== "no";
    })
    .sort(byDateThenTitle(endDateOf));
}

function hasImage(event) {
  return Boolean(event.imageUrl && event.imageUrl.trim());
}

// ① 가로 카드: 지금 진행 중 중 ② 곧 종료에 없는 것 → 사진 있는 것 먼저 → 끝나는 날 가까운 순, 최대 10
function nowOpenCards(events, now) {
  var ending = {};
  endingSoon(events, now).forEach(function (event) {
    ending[event.id] = true;
  });
  return nowOpenEvents(events, now)
    .filter(function (event) {
      return !ending[event.id];
    })
    .sort(function (a, b) {
      var diff = (hasImage(a) ? 0 : 1) - (hasImage(b) ? 0 : 1);
      return diff !== 0 ? diff : byDateThenTitle(endDateOf)(a, b);
    })
    .slice(0, HOME_NOW_MAX);
}

// ① 끝 넘김 (P3, 11/13 확정 시안 HomeOverscroll): 마지막 카드에서 밀면 끝의 좁은 안내 "더 밀면 전체" 가 보이고,
// 끝에 닿은 채로 한 번 더 밀면 지도 탭 + '진행 중' + 카드 줄. 사용자: "실제 적용해 봐야 알겠다" → 값은 운영에서 보고 조정
var HOME_END_SWIPE_PX = 40;

// 가로 줄이 끝까지 넘어갔나 (소수점 · 1px 어긋남 허용)
function atScrollEnd(scrollLeft, scrollWidth, clientWidth) {
  return scrollLeft + clientWidth >= scrollWidth - 2;
}

// 끝에 닿은 채 시작한 밀기가 "한 번 더 밀기"인가. dx · dy = 손가락이 움직인 거리 (끝 - 시작, 왼쪽으로 밀면 dx < 0)
function homeEndSwipe(startedAtEnd, dx, dy) {
  return !!startedAtEnd && -dx >= HOME_END_SWIPE_PX && Math.abs(dx) > Math.abs(dy);
}

// ③ 달별 행사: 다음 달부터 11달 뒤까지, 그 달에 시작하는 오픈예정 행사 → 행사 있는 달만
// [{ key: "2026-12", label: "12월", events: [시작일 순] }]. 이번 달 행사는 ① · ② 와 지도에서 봄
function monthGroups(events, now) {
  var parts = homeSchedule.koreaToday(now).split("-");
  var year = Number(parts[0]);
  var month = Number(parts[1]); // 1 ~ 12
  var groups = [];
  for (var i = 1; i <= HOME_MONTHS_AHEAD; i++) {
    var m = ((month - 1 + i) % 12) + 1;
    var y = year + Math.floor((month - 1 + i) / 12);
    var key = y + "-" + (m < 10 ? "0" : "") + m;
    var picks = events
      .filter(function (event) {
        return (
          homeSchedule.eventStatus(event, now) === "upcoming" && event.startDate && event.startDate.slice(0, 7) === key
        );
      })
      .sort(
        byDateThenTitle(function (event) {
          return event.startDate;
        }),
      );
    if (picks.length) groups.push({ key: key, label: (typeof I18n !== "undefined" ? I18n : require("./i18n.js").I18n).month(m), events: picks });
  }
  return groups;
}

// 행사의 구: 서버가 좌표로 구한 값(D1) 먼저, 없으면(동기화 전 · 수원 밖) 원문 장소 글에서 찾음
var HOME_DISTRICT_PATTERN = /(장안|권선|팔달|영통)구/;
function eventDistrict(event) {
  if (!event) return null;
  if (event.district) return event.district;
  var m = HOME_DISTRICT_PATTERN.exec(event.info || "");
  return m ? m[0] : null;
}

// 구 필터 (null = 전체). 구를 모르는 행사는 구를 고르면 빠짐
function matchesDistrict(event, district) {
  return !district || eventDistrict(event) === district;
}

// 홈 "내 주변" 구 칩 순서 (디자인 01: 장안 · 권선 · 팔달 · 영통). 처음 고른 구 = 팔달구 (지도 첫 위치 행궁동)
var HOME_DISTRICTS = ["장안구", "권선구", "팔달구", "영통구"];
var HOME_DEFAULT_DISTRICT = "팔달구";

// 내 주변 "○○구 행사 N개": 그 구의 종료 안 된 행사 (지도 탭 기본 = 지난 행사 숨김과 같은 기준, 날짜 없는 행사 포함)
function districtEvents(events, district, now) {
  return events.filter(function (event) {
    return matchesDistrict(event, district) && homeSchedule.eventStatus(event, now) !== "ended";
  });
}

// ----- 검색 (디자인 06 · 07): 띄어쓰기 · 대소문자 무시, 제목 + 장소 (11/4 결정 3번, 강조는 제목만) -----
var SEARCH_SUGGEST_MAX = 20; // 입력 중 행 수

function searchNormalize(text) {
  return String(text || "").replace(/\s+/g, "").toLowerCase();
}

// ----- 한글 자음 · 치는 중 글자 (11/9) -----
// 자음만 친 글자(ㅁ)는 그 자음으로 시작하는 글자(마 · 모 · 미 …)와 맞음 → "ㅁ" · "ㅅㅇ"(수원)도 찾음
// 치는 중(typing): 마지막 글자가 아직 바뀔 수 있음 → "숭"(수 + ㅇ 받침) = 수원, "수워" = 수원 처럼 넓혀 찾음
var HANGUL_FIRST = 0xac00;
var HANGUL_LAST = 0xd7a3;
var INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"; // 첫소리 순서 (유니코드 한글 글자 순서와 같음)
// 받침 → [남는 받침, 다음 글자 첫소리]: 받침 뒤에 모음을 치면 받침(또는 겹받침의 뒤 자음)이 다음 글자로 넘어감
var FINAL_SPLIT = [
  null, [0, 0], [0, 1], [1, 9], [0, 2], [4, 12], [4, 18], [0, 3], [0, 5], [8, 0], [8, 6], [8, 7], [8, 9], [8, 16],
  [8, 17], [8, 18], [0, 6], [0, 7], [17, 9], [0, 9], [0, 10], [0, 11], [0, 12], [0, 14], [0, 15], [0, 16], [0, 17], [0, 18],
];
// 받침 → 이어서 치면 될 수 있는 겹받침 (ㄱ → ㄳ, ㄴ → ㄵ ㄶ, ㄹ → ㄺ ~ ㅀ, ㅂ → ㅄ): "달" → 닭
var FINAL_NEXT = { 1: [3], 4: [5, 6], 8: [9, 10, 11, 12, 13, 14, 15], 17: [18] };
// 모음 → 이어서 치면 될 수 있는 겹모음 (ㅗ → ㅘ ㅙ ㅚ, ㅜ → ㅝ ㅞ ㅟ, ㅡ → ㅢ, ㅘ → ㅙ, ㅝ → ㅞ)
var VOWEL_NEXT = { 8: [9, 10, 11], 9: [10], 13: [14, 15, 16], 14: [15], 18: [19] };

function syllable(initial, vowel, final) {
  return String.fromCharCode(HANGUL_FIRST + (initial * 21 + vowel) * 28 + final);
}
// 그 첫소리로 시작하는 글자 전부 + 자음 그 자체
function initialClass(initial) {
  return "[" + syllable(initial, 0, 0) + "-" + syllable(initial, 20, 27) + INITIALS[initial] + "]";
}
function escapeRegExp(ch) {
  return ch.replace(/[.*+?^${}()|[\]\\\-]/g, "\\$&");
}

// 검색어 → 정규식 (띄어쓰기 · 대소문자는 searchNormalize 로 이미 뺌). 빈 검색어면 null
var searchPatternCache = {};
function searchPattern(keyword, typing) {
  var kw = searchNormalize(keyword);
  if (!kw) return null;
  var key = (typing ? "1" : "0") + kw;
  if (searchPatternCache[key]) return searchPatternCache[key];
  var chars = Array.from(kw);
  var source = chars
    .map(function (ch, i) {
      var initial = INITIALS.indexOf(ch);
      if (initial !== -1) return initialClass(initial);
      var code = ch.charCodeAt(0);
      if (!typing || i !== chars.length - 1 || code < HANGUL_FIRST || code > HANGUL_LAST) return escapeRegExp(ch);
      // 치는 중인 마지막 글자
      var n = code - HANGUL_FIRST;
      var first = Math.floor(n / 588);
      var vowel = Math.floor((n % 588) / 28);
      var final = n % 28;
      if (final === 0) {
        // 받침 없음 → 같은 첫소리 + (같은 모음 · 이어질 겹모음) + 아무 받침
        return (
          "[" +
          [vowel]
            .concat(VOWEL_NEXT[vowel] || [])
            .map(function (v) {
              return syllable(first, v, 0) + "-" + syllable(first, v, 27);
            })
            .join("") +
          "]"
        );
      }
      // 받침 있음 → 그 글자 · 이어질 겹받침 글자(달 → 닭), 또는 받침이 다음 글자로 넘어간 모양(숭 → 수 + ㅇ으로 시작하는 글자)
      var same = [final].concat(FINAL_NEXT[final] || []).map(function (f) {
        return syllable(first, vowel, f);
      });
      var split = FINAL_SPLIT[final];
      return "(?:[" + same.join("") + "]|" + syllable(first, vowel, split[0]) + initialClass(split[1]) + ")";
    })
    .join("");
  var pattern = new RegExp(source);
  searchPatternCache[key] = pattern;
  return pattern;
}

// 검색어가 비면 모두 맞음 (지도 탭 필터에서 씀). typing = 치는 중 규칙 (검색 화면 입력 중, searchTyping 참고)
function matchesSearch(event, keyword, typing) {
  var pattern = searchPattern(keyword, typing);
  if (!pattern) return true;
  return pattern.test(searchNormalize(event.title)) || pattern.test(searchNormalize(event.place));
}

// 입력 중 화면에서 치는 중 규칙을 쓸지: 그대로 찾아 하나도 없고, 넓히면 달라질 때만 ("미"는 그대로 → 민 · 밀까지 넓히지 않음)
function searchTyping(events, keyword) {
  var strict = searchPattern(keyword, false);
  if (!strict || strict.source === searchPattern(keyword, true).source) return false;
  return !events.some(function (event) {
    return matchesSearch(event, keyword, false);
  });
}

// 치는 중 규칙을 쓸지 — 모바일 검색 화면(입력 중) · PC 목록 검색 공통 (11/13)
//  composing = 마지막 글자를 아직 IME 로 조합하는 중 (input 이벤트 isComposing) → 늘 넓힘:
//    "광복"을 치는 중 "과" 는 다음 키로 광 · 괄 … 이 되므로, 그대로 맞는 행사(치킨'과')가 있어도 광복을 빼지 않음
//    순서는 searchEvents(묶음마다) · searchLooseLast(PC 목록) 가 그대로 맞는 행사를 위에 둠 ("미" → 미술 먼저, 시민은 아래)
//  조합이 끝난 글자는 예전처럼 그대로 찾아 하나도 없을 때만 넓힘 (조합 이벤트를 안 내는 키보드 대비)
function searchTypingMode(events, keyword, composing) {
  if (composing) return !!searchPattern(keyword, false);
  return searchTyping(events, keyword);
}

// 검색칸 조합 상태 감시 (한글 IME): 조합 중 입력은 치는 중 규칙으로, 조합이 끝나면(스페이스 · 다른 칸 · 엔터) 그대로 규칙으로 다시 거름
//  input = 검색 입력칸, setComposing = 조합 상태를 적을 곳, rerender = 조합이 끝난 뒤 다시 거를 함수
//  Chrome: input(isComposing) → compositionend → 여기서 다시 거름 / Safari: compositionend → input(isComposing false) 이 거름
//  같은 키로 바로 다음 글자 조합이 시작되면(광 → 광ㅂ: end → start) 다시 거르지 않음 (다음 input 이 거름)
//  schedule = 나중에 부를 함수 (기본 setTimeout 0, 시험에서 바꿈)
function watchSearchComposition(input, setComposing, rerender, schedule) {
  var later =
    schedule ||
    function (fn) {
      setTimeout(fn, 0);
    };
  var composing = false;
  input.addEventListener("compositionstart", function () {
    composing = true;
    setComposing(true);
  });
  input.addEventListener("compositionend", function () {
    composing = false;
    setComposing(false);
    later(function () {
      if (!composing) rerender();
    });
  });
}

// 넓혀서만 맞는 것(그대로는 안 맞음)인지 — typing 이 아니면 늘 false
function searchLooseOnly(event, keyword, typing) {
  return !!typing && !matchesSearch(event, keyword, false);
}

// 이미 정렬한 목록에서 넓혀서만 맞는 것을 아래로 (나머지 순서는 그대로). getEvent = 줄 → 행사 (없으면 줄 그대로)
function searchLooseLast(items, keyword, typing, getEvent) {
  if (!typing) return items;
  var strict = [];
  var loose = [];
  items.forEach(function (item) {
    (searchLooseOnly(getEvent ? getEvent(item) : item, keyword, typing) ? loose : strict).push(item);
  });
  return strict.concat(loose);
}

// 묶음: 운영중 · 오픈예정 0 → 날짜 없음 1 → 종료 2
function searchGroup(event, now) {
  var status = homeSchedule.eventStatus(event, now);
  if (status === "open" || status === "upcoming") return 0;
  return status === "ended" ? 2 : 1;
}

// 맞는 행사: 운영중 · 오픈예정(종료 임박순) → 날짜 없음(제목 순) → 종료(최근에 끝난 순). 검색어가 비면 []
//  typing(치는 중 규칙)이면 묶음마다 그대로 맞는 행사 → 넓혀서만 맞는 행사
function searchEvents(events, keyword, now, typing) {
  if (!searchNormalize(keyword)) return [];
  var endOf = function (event) {
    return event.endDate || event.startDate || "";
  };
  return events
    .filter(function (event) {
      return matchesSearch(event, keyword, typing);
    })
    .sort(function (a, b) {
      var ga = searchGroup(a, now);
      var gb = searchGroup(b, now);
      if (ga !== gb) return ga - gb;
      // 같은 묶음 안에서 그대로 맞는 행사 먼저 (진행 중 행사는 넓혀서 맞아도 종료 행사보다 위 → 입력 중 20줄 안에 듦)
      var la = searchLooseOnly(a, keyword, typing);
      var lb = searchLooseOnly(b, keyword, typing);
      if (la !== lb) return la ? 1 : -1;
      var diff = ga === 2 ? endOf(b).localeCompare(endOf(a)) : endOf(a).localeCompare(endOf(b));
      return diff !== 0 ? diff : (a.title || "").localeCompare(b.title || "");
    });
}

// 제목에서 검색어 자리 (띄어쓰기 무시) → { start, end } (원래 제목 기준, end 는 제외). 없으면 null
function searchHighlight(title, keyword, typing) {
  var pattern = searchPattern(keyword, typing);
  if (!pattern || !title) return null;
  var chars = [];
  var index = [];
  for (var i = 0; i < title.length; i++) {
    if (/\s/.test(title[i])) continue;
    var lower = title[i].toLowerCase(); // 소문자로 바꾸면 길어지는 글자(İ → i̇)도 있어 바뀐 글자마다 원래 자리를 적음
    for (var j = 0; j < lower.length; j++) {
      chars.push(lower[j]);
      index.push(i);
    }
  }
  var found = pattern.exec(chars.join(""));
  if (!found) return null;
  return { start: index[found.index], end: index[found.index + found[0].length - 1] + 1 };
}

// 검색어가 구 이름 앞부분과 맞으면 그 구 ("팔달" · "팔달구" → 팔달구). 한 글자는 너무 넓어서 뺌
function searchDistrict(keyword) {
  var kw = searchNormalize(keyword);
  if (kw.length < 2) return null;
  for (var i = 0; i < HOME_DISTRICTS.length; i++) {
    if (HOME_DISTRICTS[i].indexOf(kw) === 0) return HOME_DISTRICTS[i];
  }
  return null;
}

if (typeof module !== "undefined") {
  module.exports = {
    SEARCH_SUGGEST_MAX: SEARCH_SUGGEST_MAX,
    matchesSearch: matchesSearch,
    searchTyping: searchTyping,
    searchTypingMode: searchTypingMode,
    searchLooseOnly: searchLooseOnly,
    searchLooseLast: searchLooseLast,
    watchSearchComposition: watchSearchComposition,
    searchEvents: searchEvents,
    searchHighlight: searchHighlight,
    searchDistrict: searchDistrict,
    HOME_DISTRICTS: HOME_DISTRICTS,
    HOME_DEFAULT_DISTRICT: HOME_DEFAULT_DISTRICT,
    districtEvents: districtEvents,
    eventDistrict: eventDistrict,
    matchesDistrict: matchesDistrict,
    HOME_NOW_MAX: HOME_NOW_MAX,
    endingSoon: endingSoon,
    startingSoon: startingSoon,
    nowOpenEvents: nowOpenEvents,
    nowOpenCards: nowOpenCards,
    HOME_END_SWIPE_PX: HOME_END_SWIPE_PX,
    atScrollEnd: atScrollEnd,
    homeEndSwipe: homeEndSwipe,
    monthGroups: monthGroups,
  };
}
