# 출처 후보 검토 — 경기문화재단 지지씨(GGC) · 경기데이터드림 "문화행사 현황"

> 11/11 분기 세션 검토. **코드는 넣지 않음** (등급 S, 문서만).
> 결론 먼저: **보류 — API 에 장소 · 주소 · 좌표가 아예 없어 "수원만 거르기"와 지도 표시가 상세 페이지(HTML) 크롤링 없이는 안 되고, 11/11 표본의 수원 행사 10건 중 8건은 이미 재단(SWCF)에 있음.**

## 0. 확인 범위 · 한계

| 항목 | 확인 방법 | 결과 |
|---|---|---|
| 지지씨 API 명세 | `https://ggc.ggcf.kr/openAPI` 페이지 (WebFetch) | ✅ 주소 · 파라미터 · 응답 항목 이름 (아래 1 · 2번) |
| 지지씨 API 실제 응답 | `/open/json/playongoing` 를 **KEY 없이** 호출 (11/11 WebFetch, page 0 · 1 · 3) | ✅ 응답 옴. 항목 이름이 명세와 다름(2번) |
| 경기데이터드림 "문화행사 현황" | `openapi.gg.go.kr/GGCULTUREVENTSTUS` KEY 없이 호출 (11/11) | ✅ 표본 5행만 옴 — 항목 이름 · 총건수 확인 |
| 공개 코드 사례 | GitHub 공개 저장소 3곳 (2번 끝) | ✅ 같은 주소 · 같은 오타 처리 확인 |
| 우리 운영 데이터와 겹침 | `https://suwonbible.com/api/events` (11/11) 와 지지씨 표본 비교 | ✅ 3번 표 |
| 이용허락 · 라이선스 | 지지씨 상세 페이지 하단 · 오픈API 페이지 | ❓ "COPYRIGHT©GGCF. ALL RIGHTS RESERVED." 만 있음, 공공누리 표시 없음 → 5번 ① |
| 경기데이터드림 데이터셋 상세(갱신 주기 · 이용허락) | `data.gg.go.kr` · `www.data.go.kr` 상세 | ❌ 클라우드에서 열기 실패(서버 오류) → 5번 ② |
| 하루 호출 한도 | 명세 오류코드 337 "일별 트래픽 제한" | ❓ 숫자 없음 → 5번 ① 의 API 가이드 zip |

## 1. 기본 정보

| 항목 | 지지씨 GGC 오픈API | 경기데이터드림 "문화행사 현황" |
|---|---|---|
| 운영 | 경기문화재단 (경기 31개 시군 문화소식 플랫폼) | 경기도 (경기데이터드림) |
| 관계 | 원본 | **지지씨 행사의 사본** — 모든 행 `INST_NM`=경기문화재단, `URL`=지지씨 상세 주소 |
| 요청 주소 | `GET https://ggc.ggcf.kr/open/json/playongoing` | `GET https://openapi.gg.go.kr/GGCULTUREVENTSTUS` |
| 형식 | JSON (`/json/` 경로, XML 여부 ❓) | JSON · XML (`Type=json`) |
| 인증 | `KEY` (명세상 필수). **11/11 KEY 없이도 100건 응답** — 공개 코드(kids-experience-radar)도 키 없이 호출 | `KEY` (없으면 표본 5행만 — 11/11 `pSize=100` 에도 5행) |
| 페이지 | `page`(0부터) · `perpage` | `pIndex`(1부터) · `pSize` |
| 한 번 최대 | 명세 1,000건(오류 336). **실측 `perpage=1000` → 100건** | ❓ (경기데이터드림 공통 규칙 확인 필요) |
| 하루 한도 | 있음(오류 337), 숫자 ❓ | ❓ |
| 갱신 | 등록일(`created`) 11/3~11/6 행이 맨 앞 → 수시 등록으로 보임 | `WRITNG_DE` 최댓값 20261106 = 지지씨와 같은 날 → 사본이 늦지 않음 |
| 총 건수 | ❓ (총건수 칸 없음, page 3 까지 100건씩 = 2025-11 등록분까지) | `list_total_count` 3,108 · 3,171 (11/11 두 번 호출 값이 다름 ❓) |
| 지역 · 기간 파라미터 | **없음** | **없음** — `SIGUN_NM=수원시` 넣어도 총건수 그대로(3,171) |
| 다른 기능 | `/open/json/news` (소식 · 채용 · 입찰 · 지원 공고) | — |

- 이름이 "playongoing"(진행 중)이지만 **지난 행사도 섞여 옴**: page 0 의 100건 중 55건이 11/11 이전 종료. 순서는 등록일 최신순으로 보임 → 등록일이 일정 기간(예: 6개월) 이전이 될 때까지 페이지를 넘겨야 함
- 참고: data.go.kr 의 "경기도_경기문화재단 문화 공고/소식 현황"(15059026)은 **2022-11 누리집 개편 뒤 갱신 중단**이라고 적혀 있음 → 쓰지 않음

## 2. 응답 항목 → 우리 `Event` 매핑

**실제 응답 항목** (11/11, 명세와 다름)

- 지지씨: `writer` · `subject` · `category` · `href` · `thumbnail` · `startdate` · **`enddate:`**(키 이름에 콜론 오타) · `address` · `intime` · `incost` · `inquiry` · `inarea` · `homepage` · `created`
  - 명세의 `time` · `cost` · `quiry` · `area` · `memgers` 는 실제로는 `intime` · `incost` · `inquiry` · `inarea` 로 옴
  - `address` 는 **200건 모두 빈 문자열**
  - `inarea` 는 장소가 아니라 **주최 / 주관** (예: `수원시 / 수원문화재단`, `주최 : … / 주관 : …`)
- 경기데이터드림: `INST_NM` · `TITLE` · `CATEGORY_NM` · `URL` · `EVENT_TM_INFO` · `PARTCPT_EXPN_INFO` · `TELNO_INFO` · `HOST_INST_NM` · `HMPG_URL` · `IMAGE_URL` · `BEGIN_DE` · `END_DE`(yyyyMMdd) · `WRITNG_DE`

| 우리 칸 | 지지씨 | 경기데이터드림 | 비고 |
|---|---|---|---|
| `title` | `subject` | `TITLE` | 썸네일 · 제목 일부 한글 깨짐 사례 있음(11/11 관찰) |
| `startDate` · `endDate` | `startdate` · `enddate:`(없으면 `enddate`) | `BEGIN_DE` · `END_DE` | 시작일 빈 행 0건 (page 0) |
| `place` | ❌ **없음** | ❌ **없음** | 상세 페이지 HTML 의 "장소" 줄에만 있음 (예: `성남큐브미술관 기획전시실`) |
| 주소 | ❌ (`address` 빈 값) | ❌ | 상세 페이지에도 행사 주소 없음 |
| `latitude` · `longitude` | ❌ | ❌ | **장소명 → 카카오 로컬 키워드 검색(지오코딩, 주소 → 좌표 변환) 필요** — 기존 `PlaceGeocoder` 재사용 |
| `fee` | `incost` | `PARTCPT_EXPN_INFO` | 빈 값 · null 있음 |
| `contact` | `inquiry` | `TELNO_INFO` | |
| `host` | `inarea` | `HOST_INST_NM` | |
| `timeText` | `intime` | `EVENT_TM_INFO` | |
| `imageUrl` | `thumbnail` | `IMAGE_URL` | `https://ggc.ggcf.kr/uploadimg/...` |
| `link` | `href` | `URL` | `https://ggc.ggcf.kr/cultureEvents/view/<24자리 16진수>` |
| `category` | `category` | `CATEGORY_NM` | 값 4종: 전시 · 공연 · 교육 · 행사 → 우리 분류 매핑 필요 |
| `source` · `sourceKey` | `GGC` (새로) · `href` 끝 24자리 id | 같음 | `(source, source_key)` 로 중복 저장 방지 |

**공개 코드 사례** (근거)
- `kimtami/kids-experience-radar` `src/kids_experience_radar/sources/ggc_events.py` — 키 없이 `page`·`perpage=100`, `INFO`·`DATA` 검사, `row.get("enddate:") or row.get("enddate")`, 상세 주소 형식 검사, 시군은 주소 정규식으로만(주소가 비어 대부분 "경기도")
- `Hanshin-OSS-Hub/capstone25-culture-wanderers` `GgcFestivalService.java` — `KEY`(키 없으면 건너뜀) · `perpage` 10~100, 지역 필터 · 지오코딩 없음
- 같은 저장소 `server/fetchData.js` — `openapi.gg.go.kr/GGCULTUREVENTSTUS` (`KEY` · `Type` · `pIndex` · `pSize`)

## 3. 수원만 거르는 방법 · 재단과 겹침

**요청 파라미터로는 불가** (시군 · 좌표 파라미터 없음, `SIGUN_NM` 무시됨). 후처리만 가능:

| 단계 | 방법 | 문제 |
|---|---|---|
| ① 글자로 후보 | `subject` · `inarea` · `inquiry` 에 "수원" | **놓침**: page 0 의 국립농업박물관(수원 권선구) 전시 2건은 어디에도 "수원" 없음 |
| ② 상세 페이지 장소 | 행마다 `href` HTML 을 받아 "장소" 줄 추출 | **크롤링** — 이용 조건 ❓(0번), HTML 구조가 바뀌면 깨짐 |
| ③ 확정 | 장소명 → `PlaceGeocoder`(이미 "주소에 수원시 + 좌표 범위"만 FOUND) | FOUND 아니면 버림 → 이게 실제 수원 필터 |

→ 정확하게 하려면 **모든 새 행의 상세 페이지를 받아야 함**(주 수십 건). ①만 쓰면 수원 행사를 놓치고, 장소가 없어 지도에 못 찍음.

**11/11 표본 겹침** (지지씨 page 0~1 = 최신 200건 중 수원 관련, 우리 운영 `/api/events` 와 비교)

| 지지씨 제목 | 운영에 있음? | 운영 제목 · 출처 |
|---|---|---|
| 수원 로케이션 DB 체험·전시 | ✅ | 수원 로케이션 DB 전시 · 체험 (SWCF) |
| 유아동 예술교육 페스타 <키득~ 키즈 아트놀이터> | ✅ | <키득 키즈 아트 놀이터> (SWCF) |
| 2026 대니 구 윈터콘서트 | ✅ | 수원SK아트리움 기획공연 2026 대니 구 윈터콘서트 <HOME> (SWCF) |
| 창작 연희극 <줄 타는 아이와 아프리카 도마뱀> | ✅ | 줄 타는 아이와 아프리카도마뱀 - 수원 (SWCF) |
| 푸른지대창작샘터 6기 결과보고전 | ✅ | 같은 제목 (SWCF) |
| 국악인형극 연희도깨비 | ✅ | 연희도깨비 (SWCF) |
| 치킨과 함께하는 수원 K-헤리티지 미식여행 | ✅ | 같은 제목 (SWCF) |
| 국립농업박물관 테마전 <따스하게 달그락> | ✅ | (SWCF) |
| 제주밭담 재현 소개공간 '지키는 돌, 이어지는 유산' (국립농업박물관) | ❌ **새 행사** | — |
| 스테이지엠 크리스마스 영화음악 & 캐롤 콘서트 - 수원 | ❌ **새 행사** | — (장소 ❓) |

- 10건 중 **8건 중복**, 새로 늘어나는 건 2건 정도. 중복의 대부분은 주최에 "수원문화재단"이 들어간 행사
- 주의: 운영 응답은 WebFetch 요약으로 본 것 — 정확한 숫자는 5번 ③ 실측으로

**중복 판정** — 기존 합치기(`docs/event-merge.md`, `service/merge/`)에 그대로 끼움
- 기준은 그대로: 날짜 겹침 + 좌표 300m 또는 장소명 같음 + 제목 유사(`EventMatcher`). 위 표의 제목 차이(`<HOME>` 덧붙임 · "국악인형극" 앞말 · 띄어쓰기)는 포함 · 유사도 규칙으로 잡힘 (실측으로 확인)
- 대표 우선순위: `EventGrouper.rank()`(94줄)의 "새 출처 = 3" 자리에 `GGC` 를 재단(0) 다음 · 인스타(1·2) 앞으로 → 재단 0 → **GGC 1** → 인스타 개별 2 → 인스타 모음 3
- "묶음에 재단 2개 이상이면 합치지 않음" 규칙은 그대로 (GGC 는 재단으로 치지 않음)
- 추가 장치(선택): 주최에 "수원문화재단"이 있고 재단 행과 날짜가 겹치면 상세 페이지를 받지 않음 → 크롤링 수 줄이기

## 4. 넣을 때 작업 (예상 등급 L)

| # | 작업 | 메모 |
|---|---|---|
| 1 | `Event.SOURCE_GGC` + `(source, source_key=href id)` | 스키마는 V2 `source` · `source_key` 재사용 → **Flyway 없음** 예상 |
| 2 | `GgcEventClient` + `GgcEventAdapter`(응답 → Event) | `enddate:` 오타 · 빈 값 · 한글 깨짐 · 지난 행사 처리 |
| 3 | 상세 HTML 에서 "장소" 꺼내기(`GgcDetailParser`) | 이용 조건 확인 뒤에만. 1초 간격 · 하루 1회 |
| 4 | 장소 → `PlaceGeocoder` → 수원 FOUND 만 저장 | 구(`district`)는 D1 방식 |
| 5 | `EventGrouper.rank()` 에 GGC 우선순위 | 합치기 시험에 재단 ↔ GGC 쌍 추가 |
| 6 | `ScheduledSyncJob` 06 · 18시에 붙이고 운영 스위치(환경변수) | 인스타처럼 |
| 7 | 화면 · 처리방침 출처 표시 "경기문화재단 지지씨" | 라이선스 확인 뒤 문구 |
| 8 | 시험: 가짜 JSON(오타 키 포함) · 가짜 상세 HTML · 수원 아닌 장소 버림 · 재단과 합치기 | 15~20개 |

## 5. 결정을 위한 확인 (사용자 PC)

**① 지지씨 이용 조건 · API 가이드**
1. 브라우저에서 `https://ggc.ggcf.kr/openAPI` 열기 → "전체 API 명세 다운로드"(지지씨_API_가이드.zip) 받기
   - 되어 있나요? → zip 안 문서에 **하루 호출 한도 · 이용 조건(상업/비상업 · 출처 표시)** 이 있으면 이 문서 1번 표에 적기
   - 안 되어 있으면(다운로드 안 됨) → 경기문화재단에 문의: "개인 포트폴리오 지도 서비스에서 오픈API 행사 정보와 상세 페이지 장소 정보를 출처 표시하고 써도 되는지"
2. 같은 페이지에서 **인증키 신청 메뉴** 가 있는지 확인 (키 없이도 응답 오지만, 명세상 필수라 키를 받아 두는 쪽이 안전)

**② 경기데이터드림 "문화행사 현황" 상세**
1. `https://data.gg.go.kr` → 검색창에 "문화행사 현황" → 데이터셋 열기
2. **이용허락범위(공공누리 유형) · 갱신 주기 · 한 번 최대 건수** 칸 확인 → 이 문서 1번 표에 적기
   - 공공누리 1유형(출처 표시)이면 → 목록은 경기데이터드림으로 받는 안이 라이선스 면에서 깔끔
3. 장소 · 주소 칸이 정말 없는지 "미리보기" 표로 한 번 더 확인

**③ 실측** (키 없이 가능 — Claude 가 로컬에서 호출 · 결과만 기록)
- 지지씨 등록일 최근 6개월 전체 → "수원" 글자 후보 수 / 상세 장소 → `PlaceGeocoder` FOUND(수원) 수
- 운영 `/api/events` 와 합치기 기준으로 **겹치는 수 / 새로 늘어나는 수** (월 단위)

## 6. 추천 · 구현 크기

| 결과 | 판단 |
|---|---|
| 이용 조건 OK + 수원 새 행사 **월 10건 이상** | ✅ 넣기 (L, 4번 작업) |
| 상세 페이지 사용이 안 되거나 새 행사 **월 5건 미만** | ❌ 넣지 않음 — 크롤링 위험 · 합치기 오판 위험 대비 이득 작음 |

- 지금은 **보류**: 11/11 표본 기준 수원 새 행사 비율이 낮고(10건 중 2건), 장소가 API 에 없어 크롤링이 필수
- 구현 크기(넣기로 하면): **Adapter 1 + Client 1 + 상세 파서 1, Flyway 없음, 시험 15~20개, 등급 L**
- 기준 숫자(10 · 5건)는 제안값 — 실측 뒤 사용자가 정함
- 출처 후보 추천 순서는 `docs/source-candidate-swart.md` 7번 참고
