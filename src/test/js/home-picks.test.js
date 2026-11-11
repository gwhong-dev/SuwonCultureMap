// 모바일 홈 칸별 고르기 규칙 시험 — 홈 3칸(지금 진행 중 · 곧 종료 | 곧 시작 · 달별) · 내 주변 · 검색 (한국 시간 기준)
const test = require("node:test");
const assert = require("node:assert");
const {
  HOME_NOW_MAX,
  endingSoon,
  startingSoon,
  nowOpenEvents,
  nowOpenCards,
  monthGroups,
  eventDistrict,
  matchesDistrict,
  HOME_DISTRICTS,
  HOME_DEFAULT_DISTRICT,
  districtEvents,
  matchesSearch,
  searchEvents,
  searchHighlight,
  searchDistrict,
  searchTyping,
  SEARCH_SUGGEST_MAX,
} = require("../../main/resources/static/home-picks.js");

// 한국 시간 → 밀리초 (UTC 로 9시간 빼서)
function kst(y, m, d, h) {
  return Date.UTC(y, m - 1, d, (h === undefined ? 12 : h) - 9);
}
const WED = kst(2026, 11, 4); // 2026-11-04 수요일 12시 → 7일 안 = 11/4 ~ 11/10

function ev(id, startDate, endDate, title) {
  return { id, title: title || "행사" + id, startDate, endDate };
}
function ids(list) {
  return list.map((e) => e.id);
}

// ----- ② 곧 종료 | 곧 시작 (7일 안 = 오늘 포함 7일, 지도 칩 "7일 안"과 같음) -----

test("곧 종료: 운영중이고 종료가 7일 안(오늘 ~ +6일), 종료 가까운 순 · 같은 날은 제목 순, 개수 제한 없음", () => {
  const events = [
    ev(1, "2026-10-01", "2026-12-31"), // 종료 멀음 → 뺌
    ev(2, "2026-11-01", "2026-11-04", "나 오늘 종료"),
    ev(3, "2026-11-01", "2026-11-04", "가 오늘 종료"),
    ev(4, "2026-11-05", "2026-11-06"), // 오픈예정 → 뺌
    ev(5, "2026-10-01", "2026-11-10"), // +6일 → 넣음
    ev(6, "2026-10-01", "2026-11-11"), // +7일 → 뺌
    ev(7, "2026-10-01", "2026-11-03"), // 어제 종료(지난 행사) → 뺌
    ev(8, "2026-10-20", "2026-11-08"),
  ];
  assert.deepStrictEqual(ids(endingSoon(events, WED)), [3, 2, 8, 5]);
});

test("곧 시작: 시작 전이고 7일 안에 시작, 시작일 가까운 순", () => {
  const events = [
    ev(1, "2026-12-01", "2026-12-02"), // 7일 넘음 → 뺌
    ev(2, "2026-11-04", "2026-11-05"), // 오늘 시작 = 운영중 → 뺌
    ev(3, "2026-11-05", "2026-11-05"),
    ev(4, "2026-11-10", "2026-11-21"), // +6일 → 넣음
    ev(5, "2026-11-06", "2026-11-30"),
    ev(6, "2026-11-11", "2026-11-12"), // +7일 → 뺌
  ];
  assert.deepStrictEqual(ids(startingSoon(events, WED)), [3, 5, 4]);
});

test("곧 종료 · 곧 시작: 월말 경계 (11/28 → 12/4 까지)", () => {
  const now = kst(2026, 11, 28);
  const events = [
    ev(1, "2026-11-01", "2026-12-04"), // 곧 종료 (+6일)
    ev(2, "2026-11-01", "2026-12-05"), // +7일 → 뺌
    ev(3, "2026-12-04", "2026-12-10"), // 곧 시작 (+6일)
    ev(4, "2026-12-05", "2026-12-10"), // +7일 → 뺌
  ];
  assert.deepStrictEqual(ids(endingSoon(events, now)), [1]);
  assert.deepStrictEqual(ids(startingSoon(events, now)), [3]);
});

test("곧 종료 · 곧 시작: 연말 경계 (12/29 → 다음 해 1/4 까지), 한국 시간 자정 기준", () => {
  const now = kst(2026, 12, 29, 0); // 한국 12/29 0시 = UTC 12/28 15시
  const events = [
    ev(1, "2026-12-01", "2027-01-04"), // 곧 종료
    ev(2, "2026-12-01", "2027-01-05"), // 뺌
    ev(3, "2027-01-04", "2027-01-31"), // 곧 시작
    ev(4, "2027-01-05", "2027-01-31"), // 뺌
    ev(5, "2026-12-01", "2026-12-28"), // 한국 기준 어제 종료 → 뺌
  ];
  assert.deepStrictEqual(ids(endingSoon(events, now)), [1]);
  assert.deepStrictEqual(ids(startingSoon(events, now)), [3]);
});

test("곧 종료 · 곧 시작: 종료일 없는 행사 = 시작일 하루, 날짜 없는 행사는 뺌", () => {
  const events = [
    ev(1, null, null), // 날짜 없음 → 둘 다 뺌
    ev(2, "2026-11-04", null), // 오늘 하루 → 곧 종료 (오늘 종료)
    ev(3, null, "2026-11-10"), // 시작일 없이 종료만 → 운영중, 곧 종료
    ev(4, "2026-11-06", null), // 앞으로 하루 → 곧 시작
    ev(5, "2026-11-20", null), // 7일 넘음 → 뺌
    ev(6, "2026-11-03", null), // 어제 하루(지난 행사) → 뺌
  ];
  assert.deepStrictEqual(ids(endingSoon(events, WED)), [2, 3]);
  assert.deepStrictEqual(ids(startingSoon(events, WED)), [4]);
});

test("곧 종료 · 곧 시작: 해당 행사가 없으면 빈 배열", () => {
  assert.deepStrictEqual(endingSoon([ev(1, "2026-12-01", "2026-12-02")], WED), []);
  assert.deepStrictEqual(startingSoon([ev(1, "2026-10-01", "2026-11-30")], WED), []);
});

// ----- ① 지금 진행 중 -----

test("지금 진행 중: 운영중이고 오늘 진행 (진행 날짜를 알면 오늘이 그중 하나), 지난 · 예정 · 날짜 없음 빼고", () => {
  const events = [
    ev(1, "2026-10-01", "2026-12-31"),
    { ...ev(2, "2026-11-01", "2026-11-20"), scheduleStatus: "DATES", dates: ["2026-11-01", "2026-11-20"] }, // 오늘 쉼 → 뺌
    { ...ev(3, "2026-11-01", "2026-11-20"), scheduleStatus: "DATES", dates: ["2026-11-04", "2026-11-20"] }, // 오늘 진행
    ev(4, "2026-11-05", "2026-11-06"), // 오픈예정 → 뺌
    ev(5, "2026-10-01", "2026-11-03"), // 지난 행사 → 뺌
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-11-30"), // 시작일 없음 → 뺌 ("오늘 진행" 목록 필터 matchRunDays 와 같게, 수 N = 목록 수)
    ev(8, "2026-11-04", null), // 오늘 하루
  ];
  assert.deepStrictEqual(ids(nowOpenEvents(events, WED)), [8, 3, 1]);
});

test("진행 중 카드: 곧 종료에 나온 행사는 빼서 ① · ② 가 겹치지 않음 (수 N 은 진행 중 전체)", () => {
  const events = [
    ev(1, "2026-10-01", "2026-12-31"), // 진행 중 · 종료 멀음 → 카드
    ev(2, "2026-10-01", "2026-11-06"), // 진행 중 · 곧 종료 → 카드에서 뺌
    ev(3, "2026-11-04", null), // 오늘 하루 = 곧 종료 → 카드에서 뺌
    ev(4, "2026-10-01", "2026-11-11"), // 종료 +7일 → 카드
  ];
  const cards = ids(nowOpenCards(events, WED));
  const ending = ids(endingSoon(events, WED));
  assert.deepStrictEqual(cards, [4, 1]);
  assert.deepStrictEqual(ending, [3, 2]);
  assert.ok(cards.every((id) => !ending.includes(id)));
  assert.strictEqual(nowOpenEvents(events, WED).length, 4);
});

test("진행 중 카드: 사진 있는 것 먼저 → 종료 가까운 순, 최대 10장", () => {
  const pic = (e) => ({ ...e, imageUrl: "https://example.com/" + e.id + ".jpg" });
  const events = [
    ev(1, "2026-10-01", "2026-11-20"), // 사진 없음, 종료 빠름 → 사진 있는 것 뒤
    pic(ev(2, "2026-10-01", "2026-12-31")),
    pic(ev(3, "2026-10-01", "2026-11-30")),
    { ...ev(4, "2026-10-01", "2026-11-15"), imageUrl: "  " }, // 공백 = 사진 없음
  ];
  assert.deepStrictEqual(ids(nowOpenCards(events, WED)), [3, 2, 4, 1]);
  const many = Array.from({ length: 12 }, (_, i) => ev(i + 1, "2026-10-01", "2026-12-" + String(10 + i)));
  assert.strictEqual(nowOpenCards(many, WED).length, HOME_NOW_MAX);
  assert.strictEqual(HOME_NOW_MAX, 10);
});

// ----- ③ 달별 행사 -----

test("달별: 다음 달부터, 그 달에 시작하는 오픈예정 행사만 · 행사 있는 달만 · 시작일 순", () => {
  const events = [
    ev(1, "2026-11-20", "2026-11-25"), // 이번 달 시작 → 뺌 (①·② · 지도에서)
    ev(2, "2026-12-10", "2026-12-12"),
    ev(3, "2026-12-01", "2027-02-01"), // 12월 시작 (길어도 시작 달에만)
    ev(4, "2027-02-14", null), // 종료일 없음 → 시작일 달
    ev(5, "2026-10-01", "2026-12-31"), // 운영중(12월까지 이어져도) → 뺌
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-12-20"), // 시작일 없음 → 운영중 → 뺌
  ];
  const groups = monthGroups(events, WED);
  assert.deepStrictEqual(
    groups.map((g) => [g.key, g.label, ids(g.events)]),
    [
      ["2026-12", "12월", [3, 2]],
      ["2027-02", "2월", [4]],
    ],
  );
});

test("달별: 연말 경계 — 12월이면 다음 해 1월부터, 11달 뒤까지 (12달 뒤 같은 달 이름은 뺌)", () => {
  const now = kst(2026, 12, 15);
  const events = [
    ev(1, "2026-12-20", "2026-12-21"), // 이번 달 → 뺌
    ev(2, "2027-01-03", "2027-01-05"),
    ev(3, "2027-11-01", "2027-11-02"), // 11달 뒤 → 넣음
    ev(4, "2027-12-01", "2027-12-02"), // 12달 뒤 → 뺌
  ];
  assert.deepStrictEqual(
    monthGroups(events, now).map((g) => [g.key, g.label]),
    [
      ["2027-01", "1월"],
      ["2027-11", "11월"],
    ],
  );
});

test("달별: 월말 밤 · 새 달 새벽도 한국 시간으로 달을 나눔", () => {
  const events = [ev(1, "2026-12-01", "2026-12-02")];
  assert.deepStrictEqual(ids(monthGroups(events, kst(2026, 11, 30, 23))[0].events), [1]); // 11월 말일 밤 → 12월 칩
  assert.deepStrictEqual(monthGroups(events, kst(2026, 12, 1, 1)), []); // 한국 12/1 새벽(UTC 로는 11/30) → 이번 달
});

test("구: 서버 값(좌표 기준)이 원문 글보다 먼저", () => {
  assert.strictEqual(eventDistrict({ district: "영통구", info: "장소: 팔달구 가상홀" }), "영통구");
  assert.strictEqual(eventDistrict({ district: null, info: "장소: 수원시 팔달구 가상홀" }), "팔달구");
  assert.strictEqual(eventDistrict({ info: "장소: 화성시 가상홀" }), null);
  assert.strictEqual(eventDistrict(null), null);
});

test("구 필터: null 이면 전체, 고르면 그 구만 (구를 모르면 빠짐)", () => {
  const paldal = { district: "팔달구" };
  const unknown = { district: null, info: "" };
  assert.ok(matchesDistrict(paldal, null));
  assert.ok(matchesDistrict(unknown, null));
  assert.ok(matchesDistrict(paldal, "팔달구"));
  assert.ok(!matchesDistrict(paldal, "권선구"));
  assert.ok(!matchesDistrict(unknown, "팔달구"));
});

test("내 주변 구 칩: 장안 · 권선 · 팔달 · 영통, 처음은 팔달구", () => {
  assert.deepStrictEqual(HOME_DISTRICTS, ["장안구", "권선구", "팔달구", "영통구"]);
  assert.ok(HOME_DISTRICTS.includes(HOME_DEFAULT_DISTRICT));
  assert.strictEqual(HOME_DEFAULT_DISTRICT, "팔달구");
});

test("내 주변 행사 수: 그 구의 종료 안 된 행사 (날짜 없는 행사 포함, 구 모르면 빠짐)", () => {
  const events = [
    { id: 1, district: "팔달구", startDate: "2026-11-01", endDate: "2026-11-10" }, // 운영중
    { id: 2, district: "팔달구", startDate: "2026-11-20", endDate: "2026-11-30" }, // 오픈예정
    { id: 3, district: "팔달구", startDate: "2026-10-01", endDate: "2026-11-03" }, // 종료 (어제)
    { id: 4, district: "팔달구" }, // 날짜 없음 → 지도처럼 보임
    { id: 5, district: "권선구", startDate: "2026-11-01", endDate: "2026-11-10" },
    { id: 6, district: null, info: "장소: 수원시 팔달구 가상홀", startDate: "2026-11-04" }, // 서버 값 없으면 원문 글
    { id: 7, district: null, info: "", startDate: "2026-11-04" }, // 구 모름
  ];
  assert.deepStrictEqual(
    districtEvents(events, "팔달구", WED).map((e) => e.id),
    [1, 2, 4, 6],
  );
  assert.deepStrictEqual(
    districtEvents(events, "권선구", WED).map((e) => e.id),
    [5],
  );
  assert.deepStrictEqual(districtEvents(events, "영통구", WED), []);
});

test("검색 일치: 띄어쓰기 · 대소문자 무시, 제목 + 장소", () => {
  const event = { title: "발레 <돈키호테>", place: "수원SK아트리움 대공연장" };
  assert.ok(matchesSearch(event, "돈키"));
  assert.ok(matchesSearch(event, "발레<돈")); // 제목 띄어쓰기 무시
  assert.ok(matchesSearch(event, "sk 아트리움")); // 장소 · 소문자 · 검색어 띄어쓰기
  assert.ok(matchesSearch(event, "  ")); // 빈 검색어 = 전체
  assert.ok(!matchesSearch(event, "재즈"));
  assert.ok(!matchesSearch({ title: "행사", place: null }, "광교"));
});

test("검색 정렬: 운영중 · 오픈예정(종료 임박순) → 날짜 없음 → 종료(최근 끝난 순)", () => {
  const events = [
    { id: 1, title: "재즈 A", startDate: "2026-10-01", endDate: "2026-10-05" }, // 종료
    { id: 2, title: "재즈 B", startDate: "2026-11-20", endDate: "2026-11-30" }, // 오픈예정
    { id: 3, title: "재즈 C" }, // 날짜 없음
    { id: 4, title: "재즈 D", startDate: "2026-11-01", endDate: "2026-11-10" }, // 운영중
    { id: 5, title: "재즈 E", startDate: "2026-10-01", endDate: "2026-11-02" }, // 종료 (더 최근)
    { id: 6, title: "클래식", startDate: "2026-11-01", endDate: "2026-11-10" }, // 안 맞음
  ];
  assert.deepStrictEqual(
    searchEvents(events, "재즈", WED).map((e) => e.id),
    [4, 2, 3, 5, 1],
  );
  assert.deepStrictEqual(searchEvents(events, " ", WED), []);
});

test("검색 강조: 원래 제목 기준 자리 (띄어쓰기 · 대소문자 무시)", () => {
  assert.deepStrictEqual(searchHighlight("ASSA 재즈페스티벌", "재즈"), { start: 5, end: 7 });
  assert.deepStrictEqual(searchHighlight("ASSA 재즈페스티벌", "assa재"), { start: 0, end: 6 });
  assert.deepStrictEqual(searchHighlight("수원 화성 문화제", "화성문화"), { start: 3, end: 8 });
  assert.strictEqual(searchHighlight("발레", "SK"), null); // 장소로만 맞으면 강조 없음
  assert.strictEqual(searchHighlight("발레", ""), null);
  assert.deepStrictEqual(searchHighlight("İstanbul 재즈", "재즈"), { start: 9, end: 11 }); // 소문자가 길어지는 글자 (검증 ⚠️)
});

test("자음 검색: 'ㅁ' = ㅁ으로 시작하는 글자, 'ㅅㅇ' · '수ㅇ' = 수원 (아이폰 ⑧ 확인, 11/9)", () => {
  const event = { title: "치킨과 함께하는 수원 K-헤리티지 미식여행", place: "행궁광장" };
  assert.ok(matchesSearch(event, "ㅁ")); // 미
  assert.ok(matchesSearch(event, "ㅅㅇ")); // 수원
  assert.ok(matchesSearch(event, "수ㅇ"));
  assert.ok(matchesSearch(event, "ㅎㄱ")); // 장소 행궁
  assert.ok(!matchesSearch(event, "ㅃ"));
  assert.deepStrictEqual(searchHighlight(event.title, "ㅁ"), { start: 19, end: 20 }); // 미
  assert.deepStrictEqual(searchHighlight(event.title, "ㅅㅇ"), { start: 9, end: 11 }); // 수원
  assert.ok(matchesSearch({ title: "ㅁㅁ 모임", place: "" }, "ㅁㅁ")); // 자음 그 자체도 맞음
  // 정규식 기호는 글자 그대로
  assert.ok(matchesSearch({ title: "a.b [특별] K-팝", place: "" }, "a.b"));
  assert.ok(!matchesSearch({ title: "axb", place: "" }, "a.b"));
  assert.ok(matchesSearch({ title: "a.b [특별] K-팝", place: "" }, "[특별]"));
  assert.ok(matchesSearch({ title: "a.b [특별] K-팝", place: "" }, "k-"));
});

test("치는 중 규칙: 그대로 찾아 없을 때만 마지막 글자를 넓힘 (숭 · 수워 → 수원, 고 → 과, 달 → 닭)", () => {
  const events = [
    { id: 1, title: "수원 화성 문화제", place: "" },
    { id: 2, title: "과자 만들기", place: "" },
    { id: 3, title: "닭강정 축제", place: "" },
    { id: 4, title: "시민 음악회", place: "" },
    { id: 5, title: "미술 체험", place: "" },
  ];
  const ids = (keyword) => searchEvents(events, keyword, WED, searchTyping(events, keyword)).map((e) => e.id);
  assert.ok(searchTyping(events, "숭"));
  assert.deepStrictEqual(ids("숭"), [1]); // 수 + ㅇ 받침 → 다음 글자 ㅇ
  assert.deepStrictEqual(ids("수워"), [1]); // 워 → 원
  assert.deepStrictEqual(ids("고"), [2]); // ㅗ → ㅘ
  assert.deepStrictEqual(ids("달"), [3]); // ㄹ → ㄺ
  assert.deepStrictEqual(searchHighlight("수원 화성 문화제", "숭", true), { start: 0, end: 2 });
  // 그대로 맞는 게 있으면 넓히지 않음: "미" → 미술(5)만, 시민(민)은 안 나옴
  assert.ok(!searchTyping(events, "미"));
  assert.deepStrictEqual(ids("미"), [5]);
  // 자음만 · 빈 검색어 · 한글이 아닌 끝 글자는 넓힐 것이 없음
  assert.ok(!searchTyping(events, "ㅁ"));
  assert.ok(!searchTyping(events, " "));
  assert.ok(!searchTyping(events, "zz"));
  // 엔터 결과(typing 없음)는 그대로 찾음
  assert.deepStrictEqual(searchEvents(events, "숭", WED), []);
  // 겹받침이 나뉘어 받침이 남는 경우: 닭 → 달 + ㄱ, 앉 → 안 + ㅈ (검증 ⚠️)
  assert.ok(matchesSearch({ title: "달기 축제", place: "" }, "닭", true));
  assert.ok(matchesSearch({ title: "안자 마당", place: "" }, "앉", true));
  assert.ok(!matchesSearch({ title: "달기 축제", place: "" }, "닭"));
  // 띄어쓰기가 낀 제목에서도 원래 제목 기준 강조
  assert.deepStrictEqual(searchHighlight("수 원 축제", "숭", true), { start: 0, end: 3 });
});

test("검색어의 \\ · 이모지 · 정규식 기호도 글자 그대로 (예외 없음, 검증 ⚠️)", () => {
  const event = { title: "C:\\전시 🎨 (특별) 1+1", place: "" };
  for (const typing of [false, true]) {
    assert.ok(matchesSearch(event, "\\전", typing));
    assert.ok(matchesSearch(event, "🎨", typing));
    assert.ok(matchesSearch(event, "(특별)", typing));
    assert.ok(matchesSearch(event, "1+1", typing));
    assert.ok(!matchesSearch(event, "11", typing));
  }
  assert.deepStrictEqual(searchHighlight(event.title, "🎨"), { start: 6, end: 8 });
});

test("검색어가 구 이름이면 그 구 (두 글자부터)", () => {
  assert.strictEqual(searchDistrict("팔달"), "팔달구");
  assert.strictEqual(searchDistrict(" 영통 구"), "영통구");
  assert.strictEqual(searchDistrict("팔"), null);
  assert.strictEqual(searchDistrict("팔달구청"), null);
  assert.strictEqual(searchDistrict("재즈"), null);
});

test("검색 결과(07)는 개수 제한 없음 — 입력 중 행(최대 20)과 같은 순서의 전체", () => {
  const events = Array.from({ length: 25 }, (_, i) => ({
    id: i + 1,
    title: "재즈 " + String(i + 1).padStart(2, "0"),
    startDate: "2026-11-01",
    endDate: "2026-11-" + String(5 + i).padStart(2, "0"),
  }));
  const all = searchEvents(events, "재즈", WED);
  assert.strictEqual(all.length, 25);
  assert.ok(all.length > SEARCH_SUGGEST_MAX);
  assert.deepStrictEqual(all.slice(0, 3).map((e) => e.id), [1, 2, 3]); // 종료 임박순
});
