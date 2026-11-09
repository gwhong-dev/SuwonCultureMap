// 모바일 홈 칸별 고르기 규칙 시험 — 이번 주 · 곧 종료 · 곧 시작 (한국 시간 기준)
const test = require("node:test");
const assert = require("node:assert");
const {
  weekRange,
  weekLabel,
  thisWeekEvents,
  endingSoon,
  startingSoon,
  carouselPicks,
  monthRange,
  monthTitle,
  monthEvents,
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
  return Date.UTC(y, m - 1, d, (h || 12) - 9);
}
const WED = kst(2026, 11, 4); // 2026-11-04 수요일 12시

function ev(id, startDate, endDate, title) {
  return { id, title: title || "행사" + id, startDate, endDate };
}

test("이번 주: 한국 시간 월 ~ 일", () => {
  assert.deepStrictEqual(weekRange(WED), { start: "2026-11-02", end: "2026-11-08" });
  assert.deepStrictEqual(weekRange(kst(2026, 11, 2)), { start: "2026-11-02", end: "2026-11-08" }); // 월
});

test("이번 주: 일요일은 지난 월요일부터 그날까지", () => {
  assert.deepStrictEqual(weekRange(kst(2026, 11, 8)), { start: "2026-11-02", end: "2026-11-08" });
});

test("이번 주: 한국 시간 월요일 새벽(UTC 로는 일요일)도 새 주", () => {
  assert.deepStrictEqual(weekRange(kst(2026, 11, 9, 1)), { start: "2026-11-09", end: "2026-11-15" });
});

test("주 표시: 같은 달은 끝 날짜만, 달이 바뀌면 월까지", () => {
  assert.strictEqual(weekLabel({ start: "2026-11-02", end: "2026-11-08" }), "11월 2일 – 8일");
  assert.strictEqual(weekLabel({ start: "2026-10-26", end: "2026-11-01" }), "10월 26일 – 11월 1일");
  assert.strictEqual(weekLabel({ start: "2026-12-28", end: "2027-01-03" }), "12월 28일 – 1월 3일");
});

test("이번 주 행사: 기간이 겹치고 종료 안 된 것만, 날짜 없는 행사는 뺌", () => {
  const events = [
    ev(1, "2026-10-01", "2026-11-30"), // 운영중
    ev(2, "2026-11-07", "2026-11-07"), // 이번 주 토요일 하루
    ev(3, "2026-11-08", "2026-12-01"), // 일요일 시작 → 겹침
    ev(4, "2026-11-09", "2026-11-10"), // 다음 주 → 뺌
    ev(5, "2026-11-01", "2026-11-03"), // 어제 끝남 → 뺌 (이번 주와 겹쳐도)
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-11-20"), // 시작일만 없음 → 운영중으로 봄
  ];
  assert.deepStrictEqual(
    thisWeekEvents(events, WED).map((e) => e.id),
    [1, 2, 3, 7],
  );
});

test("곧 종료: 운영중만, 종료일 가까운 순 3개 (같은 날이면 제목 순)", () => {
  const events = [
    ev(1, "2026-10-01", "2026-12-31"),
    ev(2, "2026-11-01", "2026-11-04", "나 오늘 종료"),
    ev(3, "2026-11-01", "2026-11-04", "가 오늘 종료"),
    ev(4, "2026-11-05", "2026-11-06"), // 오픈예정 → 뺌
    ev(5, "2026-10-01", "2026-11-10"),
    ev(6, "2026-10-01", "2026-11-03"), // 종료 → 뺌
  ];
  assert.deepStrictEqual(
    endingSoon(events, WED).map((e) => e.id),
    [3, 2, 5],
  );
});

test("곧 시작: 오픈예정만, 시작일 가까운 순 3개", () => {
  const events = [
    ev(1, "2026-12-01", "2026-12-02"),
    ev(2, "2026-11-04", "2026-11-05"), // 오늘 시작 = 운영중 → 뺌
    ev(3, "2026-11-05", "2026-11-05"),
    ev(4, "2026-11-20", "2026-11-21"),
    ev(5, "2026-11-06", "2026-11-30"),
  ];
  assert.deepStrictEqual(
    startingSoon(events, WED).map((e) => e.id),
    [3, 5, 4],
  );
});

test("곧 종료 · 곧 시작: 해당 행사가 없으면 빈 배열", () => {
  assert.deepStrictEqual(endingSoon([ev(1, "2026-12-01", "2026-12-02")], WED), []);
  assert.deepStrictEqual(startingSoon([ev(1, "2026-10-01", "2026-11-30")], WED), []);
});

test("이번 주: 해를 넘기는 주", () => {
  assert.deepStrictEqual(weekRange(kst(2027, 1, 1)), { start: "2026-12-28", end: "2027-01-03" });
});

test("곧 종료 · 곧 시작: 날짜 없는 행사는 빼고, 한쪽 날짜만 있는 행사는 그 날 기준", () => {
  const events = [
    ev(1, null, null), // 날짜 없음 → 둘 다 뺌
    ev(2, "2026-11-04", null), // 시작일만 = 오늘 하루 → 운영중, 오늘 종료
    ev(3, null, "2026-11-10"), // 종료일만 → 운영중
    ev(4, "2026-11-06", null), // 시작일만(앞으로) → 오픈예정
    ev(5, "2026-10-01", "2026-11-05"),
  ];
  assert.deepStrictEqual(endingSoon(events, WED).map((e) => e.id), [2, 5, 3]);
  assert.deepStrictEqual(startingSoon(events, WED).map((e) => e.id), [4]);
});

// 캐러셀용: 사진 주소까지 넣은 행사
function pic(id, startDate, endDate, title) {
  return { ...ev(id, startDate, endDate, title), imageUrl: "https://example.com/" + id + ".jpg" };
}

test("캐러셀: 이번 주 행사 중 사진 있는 것만, 운영중 먼저 → 종료일 가까운 순", () => {
  const events = [
    pic(1, "2026-11-06", "2026-11-07"), // 오픈예정 (이번 주 금 ~ 토)
    pic(2, "2026-10-01", "2026-12-31"), // 운영중, 종료 늦음
    pic(3, "2026-11-01", "2026-11-05"), // 운영중, 종료 빠름
    ev(4, "2026-11-01", "2026-11-04"), // 사진 없음 → 뺌
    pic(5, "2026-11-09", "2026-11-10"), // 다음 주 → 뺌
    pic(6, "2026-11-01", "2026-11-03"), // 종료 → 뺌
    pic(7, "2026-11-05", "2026-11-05"), // 오픈예정, 1번보다 빠름
  ];
  assert.deepStrictEqual(
    carouselPicks(events, WED).map((e) => e.id),
    [3, 2, 7, 1],
  );
});

test("캐러셀: 최대 5개, 같은 종료일이면 제목 순", () => {
  const events = [6, 5, 4, 3, 2, 1].map((n) =>
    pic(n, "2026-10-01", "2026-11-30", "행사" + n),
  );
  assert.deepStrictEqual(
    carouselPicks(events, WED).map((e) => e.id),
    [1, 2, 3, 4, 5],
  );
});

test("캐러셀: 사진 주소가 빈칸 · 공백이면 사진 없음으로 봄", () => {
  const events = [
    { ...ev(1, "2026-10-01", "2026-11-30"), imageUrl: "" },
    { ...ev(2, "2026-10-01", "2026-11-30"), imageUrl: "  " },
    { ...ev(3, "2026-10-01", "2026-11-30"), imageUrl: null },
  ];
  assert.deepStrictEqual(carouselPicks(events, WED), []);
});

test("캐러셀: 종료일 없는 오픈예정은 시작일로 순서", () => {
  const events = [
    pic(1, "2026-11-07", null), // 토요일 하루
    pic(2, "2026-11-06", "2026-11-20"),
  ];
  assert.deepStrictEqual(carouselPicks(events, WED).map((e) => e.id), [1, 2]);
});

test("이달: 한국 시간 1일 ~ 말일 (말일 새벽도 그달, 1일 새벽은 새 달)", () => {
  assert.deepStrictEqual(monthRange(WED), { start: "2026-11-01", end: "2026-11-30" });
  assert.deepStrictEqual(monthRange(kst(2026, 12, 1, 1)), { start: "2026-12-01", end: "2026-12-31" }); // UTC 로는 11/30
  assert.deepStrictEqual(monthRange(kst(2028, 2, 10)), { start: "2028-02-01", end: "2028-02-29" }); // 윤년
});

test("이달 제목: \"11월의 수원 행사\"", () => {
  assert.strictEqual(monthTitle(WED), "11월의 수원 행사");
  assert.strictEqual(monthTitle(kst(2027, 1, 1, 0)), "1월의 수원 행사");
});

test("이달 행사: 이번 달과 겹치고 종료 안 된 것만, 시작일 순", () => {
  const events = [
    ev(1, "2026-11-20", "2026-11-25"), // 오픈예정, 이달
    ev(2, "2026-10-01", "2026-11-10"), // 운영중, 지난달 시작
    ev(3, "2026-11-01", "2026-11-03"), // 종료 → 뺌
    ev(4, "2026-12-01", "2026-12-05"), // 다음 달 시작 → 뺌
    ev(5, "2026-11-30", null), // 말일 하루
    ev(6, null, null), // 날짜 없음 → 뺌
    ev(7, null, "2026-11-15"), // 시작일 없이 끝만 → 운영중, 종료일로 순서
  ];
  assert.deepStrictEqual(monthEvents(events, WED).map((e) => e.id), [2, 7, 1, 5]);
});

test("이달 행사: 최대 10개, 같은 시작일이면 제목 순", () => {
  const events = Array.from({ length: 12 }, (_, i) =>
    ev(12 - i, "2026-11-10", "2026-11-20", "행사" + String(12 - i).padStart(2, "0")),
  );
  assert.deepStrictEqual(
    monthEvents(events, WED).map((e) => e.id),
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  );
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
