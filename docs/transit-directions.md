# 오시는 길(대중교통) — 구현 방법 조사 · 추천안

> 11/11 조사 (등급 S, 문서만 · 코드 없음). 계기: 실사용자 피드백 "행사 위치 찍은 지도 밑에 버스나 오시는 길을 알려주면 좋겠다."
> 표시: ✅ 공식 문서 · 저장소 코드로 확인 / ❓ 확인 못 함 → 맨 아래 "사용자 PC 에서 확인할 것"

## 0. 결론 — 추천안 1개

**👉 후보 1 "가까운 정류장 · 역 목록"을 서버에서 만들어 하루 캐시**하고, 아래에 지금의 길찾기 버튼(후보 5)을 그대로 둔다.

| 무엇 | 어떤 API | 키 |
|---|---|---|
| 🚏 가까운 버스정류장 2~3개 (이름 · 도보 N분) | **경기도_정류소 조회** `getBusStationAroundListv2` (좌표 → 반경 500m 정류소 + `distance`) | 공공데이터포털 서비스키 **새로 발급** (개발계정 자동승인 · 무료) |
| 정류장마다 **버스 번호** | 같은 서비스 `getBusStationViaRouteListv2` (정류소 → 지나는 노선 `routeName` · `routeTypeName`) | 위와 같은 키 |
| 🚇 가까운 지하철역 | **카카오 로컬** 카테고리 검색 `SW8` (`x` · `y` · `radius` · `sort=distance`) | 지금 쓰는 `kakao.rest-api-key` 그대로 |

**이유**
1. 사용자가 원한 것 = "지도 밑에 버스" → 정류장 이름 + **버스 번호**까지 보여 줄 수 있는 건 버스정보 API 뿐 (✅ 경유노선 오퍼레이션 존재)
2. 경기도 API 는 수원 버스(경기버스정보 GBIS)의 원본 시스템이고, 응답에 **거리(m)** 가 와서 "도보 N분"을 바로 계산 (✅)
3. 장소 좌표는 하루에 바뀌지 않음 → 장소 단위로 하루 1번만 부르면 호출량이 개발계정 한도(1,000/일) 안 (아래 6번 계산)
4. 키를 브라우저에 내보내지 않음 → 서버가 부르고 우리 `/api` 로 내려줌. **CSP 변경 없음**
5. 실시간 도착(후보 2)은 화면을 열 때마다 불러야 해 한도를 바로 넘을 수 있음 → **이번엔 안 함**

---

## 1. 지금 화면 (저장소 기준 ✅)

- 모바일 상세 "주변" 탭 = `index.html` `#detailLocation`: 제목 "위치" → 작은 지도 `#modalMap` → 주소 `#detailAddress` + "주소 복사" (PC 에서는 `css/index.css` 에서 숨김)
- 하단 고정 "길찾기" → 하단 창 `#routeSheet`: 카카오(앱 `kakaomap://route?…&by=PUBLICTRANSIT` 먼저, 없으면 웹) · 네이버(`/transit`) · Google(`travelmode=transit`) — `route-links.js`, 키 안 씀
- 행사 데이터: 위도 · 경도 · 주소 · 장소명(`place`) 있음
- CSP(`SecurityHeadersFilter`): `script-src 'self' dapi.kakao.com t1.kakaocdn.net; style-src 'self'; …` — **`connect-src` · `default-src` 는 없음** → 지금은 브라우저 `fetch` 가 CSP 로 막히지는 않음. 다만 서버 방식이면 `fetch` 대상이 우리 `/api`(같은 도메인)라 앞으로 `connect-src 'self'` 를 넣어도 안전

---

## 2. 후보 비교

| | 1. 근처 정류장 · 역 | 2. 실시간 도착 | 3. 재단 원문 교통 문구 | 4. 장소별 고정 안내 | 5. 길찾기 링크 개선 |
|---|---|---|---|---|---|
| 가능 | ✅ | ✅ (API 있음) | ❓ 저장소 근거로는 **없음** (3-3) | ✅ | ✅ (이미 있음) |
| 비용 | 무료 | 무료 | 무료 | 무료 (사람 시간) | 무료 |
| 키 | 공공데이터포털 서비스키(새로) + 카카오 REST(기존) | 공공데이터포털 서비스키 | 없음(기존 재단 키) | 없음 | 없음 |
| 호출 한도 | 경기도 개발계정 **1,000/일**, 카카오 카테고리 **100,000/일** | 1,000/일 → 화면 열 때마다라 **부족** | 없음(동기화 때 이미 받음) | 없음 | 없음 |
| 약관 | 공공데이터 "이용허락범위 제한 없음" ✅ / 카카오 로컬 결과 저장 ❓ | 같음 | 재단 데이터 | 우리 글 | 지도 앱 URL 스킴 (카카오 FAQ 가 "길찾기 API 대신 URL" 권장 ✅) |
| 정확도 | 정류장 위치 · 노선은 원본 데이터, 도보 분은 **직선거리 기준 추정** | 실시간 | 재단 글 그대로 | 쓴 날 기준 · 노선 개편 때 낡음 | 지도 앱 길찾기 = 가장 정확 |
| 구현 크기 | 중 (백엔드 3~4클래스 + 화면 1파일) | 중~대 (화면 갱신 · 짧은 캐시) | 소 (있다면) | 소~중 (표 + 관리) | 소 |
| 사용자 요구 충족 | 🟢 "지도 밑에 버스" | 🟡 있으면 좋음 | ❓ | 🟡 주요 장소만 | 🔴 지금도 있는데 피드백이 나옴 |

### 2-1. 후보 1 — API 별 자세히

| API | 좌표로 근처 찾기 | 거리 | 버스 번호 | 한도 · 승인 | 비고 |
|---|---|---|---|---|---|
| **경기도_정류소 조회** (data.go.kr 15080666, GBIS) | ✅ `getBusStationAroundListv2` `x`(경도) · `y`(위도) WGS84 | ✅ `distance`(m) | ✅ `getBusStationViaRouteListv2` → `routeName` · `routeTypeName` · `routeDestName`(방면) | 개발 **1,000/일**, 운영은 활용사례 등록 뒤 증가 · 개발 · 운영 **자동승인** · 무료 · 제한 없음 | 반경 **500m 고정**(바꾸는 인자 없음, GBIS 명세 설명). 응답 `stationName` · `mobileNo`(정류소 번호) · `centerYn`(중앙차로) |
| **국토부 TAGO 버스정류소정보** (data.go.kr 15098534) | ✅ "좌표기반근접정류소 목록조회" (`getCrdntPrxmtSttnList` `gpsLati` · `gpsLong`, 공개 예제 코드 기준) | ❓ 응답 예제엔 `nodenm` · `gpslati` · `gpslong` 만 → 거리는 직접 계산 | ✅ "정류소별경유노선 목록조회" | 개발 **10,000/일**, 제한 없음 · 무료 | **수원(경기) 버스가 들어 있는지 ❓** (도시코드 목록으로 확인) → 경기도 API 가 막힐 때 예비 |
| **카카오 로컬 — 카테고리 `SW8`** | ✅ `x` · `y` · `radius`(0~20,000m) · `sort=distance` | ✅ `distance`(m) | — (지하철) | 카테고리 검색 **100,000/일** 무료 | 버스정류장 카테고리 코드는 **없음** ✅ |
| 카카오 로컬 — 키워드 "버스정류장" | 키워드 + 반경으로 가능은 함 | ✅ | ❌ 노선 없음 | 키워드 검색 100,000/일 | 버스정류장이 장소로 잡히는지 · 이름 정확도 ❓ → 추천 안 함 |

> 카카오 FAQ: "길찾기 API는 Open API 및 별도의 제휴로 제공하지 않고 있습니다" → 우리 화면 안에서 경로(몇 번 타고 몇 정거장)를 계산하는 건 불가. 경로는 후보 5(지도 앱 링크)에 맡김 ✅

### 2-2. 후보 2 — 실시간 도착: **이번엔 안 함 (권장)**

- API: 경기도_버스도착정보 조회(data.go.kr 15080346) `getBusArrivalListv2`(정류소 → 노선별 `predictTime1` · `predictTime2` …) ✅, 개발 **1,000/일** · 자동승인
- 문제: 도착 정보는 몇 분마다 바뀜 → 캐시를 짧게(1~2분) 해도 **상세를 열 때마다 정류장 2~3개 × 1회**. 하루 상세 열람이 수백 번이면 바로 한도. 운영계정 증설(활용사례 등록)을 먼저 해야 함
- 대신: 정류장 이름 옆 "실시간 ›" 은 나중에 운영계정을 받은 뒤 별도 작업으로

### 2-3. 후보 3 — 재단 XML 의 교통 문구: **저장소 근거로는 없음**

- 재단 XML 은 `contentInfo` 의 (항목 `infoItem`, 내용 `infoContent`) 쌍 (`dto/xml/ContentInfoDetail.java`). 서버가 쓰는 라벨은 `InfoParser`: 장소 · 주소/소재지 · 주소 · 시간 · 이용료 · 관람료 · 참가비용 · 문의처 · 주최/주관 · 주관 · 요일
- 저장소 문서에 적힌 실제 라벨 조사(`docs/event-schedule.md` 2-2, PROGRESS "대상 칩" 보류 메모)에도 접수기간 · 접수 · 요일 · 행사요일 · 추가회차 · 기간 · 대상 · 이용연령까지만 → **"오시는 길 · 교통 · 버스 · 지하철" 라벨 기록 없음**
- 테스트 픽스처: `src/test/resources` 에는 XML 파일이 없고, 시험은 코드 안 가짜 XML 문자열만 씀 → 판단 근거 안 됨. `InfoParserTest` 의 `주차요금` 은 "요금으로 안 걸리는지" 보는 가짜 값
- `EventItem.description`(HTML 본문)은 **일부러 안 받음** → 그 안에 교통 안내가 있는지는 ❓
- 결론: 있다 해도 일부 행사뿐일 가능성이 큼 → 추천안의 보조(있으면 "재단 안내" 한 줄)로만. **확인은 9번 ①**

### 2-4. 후보 4 — 장소별 고정 안내 (우리가 직접 작성)

- 장점: 사람이 쓴 문장이라 가장 읽기 쉬움 (예: "○○역 n번 출구 → 버스 ○번 → △△ 하차 도보 5분" 같은 한 줄)
- 단점: 수십 곳을 **확인 없이 쓰면 틀린 정보**, 노선 개편 때 아무도 모르게 낡음, 인스타 행사처럼 새 장소는 빈칸
- 쓰임: 추천안이 못 하는 곳(반경 500m 안 정류장 없음 · 중앙차로 방향 혼동)만 "덧붙임" 표(장소명 → 한 줄)로. 처음엔 안 만듦

### 2-5. 후보 5 — 길찾기 버튼 개선

- 지금 방식이 "경로" 자체는 가장 정확(지도 앱이 계산). 피드백의 원인은 "버튼을 눌러야만 보임"
- 추천안 안에서 함께 개선: 주변 탭 맨 아래에 길찾기 버튼을 **탭 안에도** 노출(지금은 하단 고정 바에만) — 구현 때 결정

---

## 3. 추천안 화면 구성 (모바일 상세 "주변" 탭)

> 아래 정류장 · 버스 번호 · 역 이름 · 거리는 **모양을 보여 주는 가상 예시** (실제 값 아님)

```
위치
┌──────────── 작은 지도 (지금 그대로) ────────────┐
└─────────────────────────────────────────────┘
경기 수원시 팔달구 …                [주소 복사]

🚏 가까운 정류장
  ○○정류장            도보 3분 · 230m
    [00] [00-1] [000] [0000] +5
  △△정류장            도보 6분 · 450m
    [00] [000] …
🚇 가까운 역
  □□역               직선 1.2km
                          ※ 도보 분은 직선거리 기준 추정
[ 길찾기 (대중교통) ]   ← 지금 하단 창을 그대로 엶
```

- 정류장 2~3개: `distance` 가까운 순. **같은 이름(길 건너 양쪽)은 하나로 합치고 버스 번호는 합집합** (방면 차이는 구현 때 "○○ 방면" 작은 글씨 여부 결정)
- 버스 번호: 칩, 많으면 6개 + "+N". `routeTypeName`(일반 · 좌석 · 마을 …)으로 색 구분은 선택
- 도보 분 = `ceil(distance / 67m)` (시속 약 4km) — **직선거리**라 실제보다 짧을 수 있음 → 화면에 "직선거리 기준" 표시
- 역: 반경 1.5km 안 1~2개 (반경 값은 구현 때 실제 장소로 조정), 없으면 칸 숨김
- 불러오기 실패 · 500m 안 정류장 없음 → 칸 숨기고 길찾기 버튼만 (지금과 같은 화면)
- 출처 한 줄: "버스 정보: 경기도 버스정보(공공데이터포털)"
- PC: `#detailLocation` 이 PC 에서 숨겨져 있음 → PC 상세에 넣을지는 구현 때 (처음엔 모바일만)

---

## 4. 데이터 흐름

```
브라우저(상세 "주변" 탭 열림)
  └─ GET /api/events/{id}/transit         ← 같은 도메인 · CSP 영향 없음 · 키 노출 없음
       서버 NearbyTransitService
         ├─ 캐시 키 = 행사 좌표 소수 4자리(약 10m) 반올림  ── 있으면 바로 응답
         └─ 없으면
              ├─ 경기도 getBusStationAroundListv2(x=경도, y=위도) → 가까운 순 정류장
              ├─ 위 2~3개 정류장마다 getBusStationViaRouteListv2(stationId) → 버스 번호
              └─ 카카오 로컬 category SW8 (radius, sort=distance) → 역 1~2개
            → 합쳐서 캐시(성공 24시간 / 실패 10분) → 응답
```

- **행사 번호로만** 받는 이유: 아무 좌표나 받으면 우리 서버가 남의 호출을 대신해 주는 통로가 되고 캐시 키가 무한히 늘어남 → 우리 DB 에 있는 행사 좌표만
- 서버 vs 브라우저: 브라우저에서 바로 부르면 ① 공공데이터 서비스키 · 카카오 **REST 키(로그인에도 쓰는 키)** 가 노출 ② data.go.kr 의 CORS 허용 여부 ❓ ③ 한도를 방문자 수만큼 씀 → **서버 방식만 추천**
- 캐시 저장소: 처음엔 메모리(`ConcurrentHashMap` + 저장 시각). 재시작하면 비지만 장소 수가 적어 다시 채워도 한도 안. DB 테이블은 필요해지면
- 미리 채우기(선택): 동기화(06 · 18시) 끝에 지도에 보이는 행사 좌표를 한 번씩 → 첫 열람도 빠름. 처음엔 열람 때 채우기(lazy)로 시작

---

## 5. 구현 크기 (예상 · 등급 M — 새 외부 API · 새 비밀값 · 운영 환경변수)

| 구분 | 내용 |
|---|---|
| 설정 | `gg.bus.service-key` (application.properties 엔 `${GG_BUS_SERVICE_KEY:}` 만, 값은 dev 파일 · Docker/운영 `.env` 의 `GG_BUS_SERVICE_KEY`), test 프로필 가짜 키 `test-key`. compose · `.env.example` · `setup-docker` 연결 ✅ (11/11 키 자리만, 기능 코드는 아직) |
| 백엔드 | `GbisStationClient`(주변정류소 · 경유노선 2개 호출, RestTemplate) · `KakaoSubwayClient`(또는 `KakaoPlaceSearchClient` 에 카테고리 메서드) · `NearbyTransitService`(합치기 · 도보 분 · 같은 이름 합치기 · 캐시) · `TransitController` `GET /api/events/{id}/transit` · 응답 DTO |
| 화면 | `static/transit-nearby.js` (그리기만, `addEventListener` 방식 — CSP), `index.html` `#detailLocation` 에 칸 1개, CSS 몇 줄 |
| 테스트 | 클라이언트: `MockRestServiceServer` 로 응답 · 오류 코드(4 = 결과 없음) · 키가 로그에 안 찍히는지 / 서비스: 가까운 순 · 같은 이름 합치기 · 도보 분 · 캐시 적중 · 실패 캐시 10분 / 컨트롤러: 없는 행사 404 · 좌표 없는 행사 빈 응답 / node: 칩 "+N" · 빈 응답이면 칸 숨김 |
| 문서 | 처리방침: 사용자 정보는 안 보냄(장소 좌표만) → 변경 없을 것으로 봄, 구현 때 재확인. README 외부 API 목록 |

---

## 6. 위험 · 대응

| 위험 | 내용 | 대응 |
|---|---|---|
| 호출 한도 | 경기도 개발계정 1,000/일. 장소 1곳 = 주변 1 + 경유 3 = **4회**. 장소 60곳(재단 + 인스타, 추정)이면 하루 최대 약 240회 + 실패 재시도 | 성공 24시간 · 실패 10분 캐시, 행사 번호로만 조회. 넘치면 활용사례 등록 → 운영계정 |
| 키 만료 | 공공데이터포털 개발계정 **활용기간 만료 ❓**(기간은 신청 화면에서 확인) · 카카오 키는 기존 | 만료 시 응답 오류 → 칸 숨김(화면은 지금과 같음) + 경고 로그. 만료일을 PROGRESS "나중에 할 일"에 적어 둠 |
| API 버전 변경 | 경기도 버스 API 주소에 버전(`/busstationservice/v2/…v2`)이 들어 있음 → 새 버전이 나오면 주소가 바뀔 수 있음 | 클라이언트 1곳에 주소를 모음, 오류 코드 로그 |
| 반경 500m 고정 | 공원 · 외곽 장소는 정류장 0개일 수 있음 | 0개면 칸 숨김 → 길찾기 버튼, 필요하면 후보 4 덧붙임 표 |
| 도보 분 오차 | 직선거리 | "직선거리 기준" 표기, 분 단위 반올림 올림 |
| 카카오 결과 저장 | 로컬 API 결과를 하루 캐시해도 되는지 약관 문구 ❓ | 9번 ⑤ 확인 전까지 역은 캐시 짧게(1시간) 또는 바로 조회 |
| 응답 속도 | 첫 열람 때 외부 호출 4~5회 | 타임아웃 2~3초, 실패해도 위치 지도 · 주소는 먼저 그림(칸만 나중에 채움) |

---

## 7. 단계 제안

1. (사용자) 9번 ② 서비스키 발급 · 브라우저 시험 → ✅ 면 다음
2. PR 1 (M): 서버 `/api/events/{id}/transit` + 정류장 · 버스 번호 + 테스트
3. PR 2 (S~M): 주변 탭 화면 + 역(카카오 SW8)
4. 나중: 실시간 도착(운영계정 뒤) · 후보 4 덧붙임 표 · PC 상세

---

## 8. 출처

- 경기도 버스정보 공개 API 명세(GBIS): [주변정류소 목록조회](https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBusStationSearcharound) · [정류소 경유노선 목록조회](https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBusStationRoute) · [정류소정보 항목조회](https://www.gbis.go.kr/gbis2014/publicService.action?cmd=mBusStationInfo) · [공개키 안내](https://www.gbis.go.kr/gbis2014/publicService.action?cmd=publickKey)
- 공공데이터포털: [경기도_정류소 조회](https://www.data.go.kr/data/15080666/openapi.do) · [경기도_버스도착정보 조회](https://www.data.go.kr/data/15080346/openapi.do) · [국토교통부_(TAGO)_버스정류소정보](https://www.data.go.kr/data/15098534/openapi.do)
- 카카오: [로컬 API 개발 가이드](https://developers.kakao.com/docs/latest/ko/local/dev-guide) · [쿼터](https://developers.kakao.com/docs/latest/ko/getting-started/quota) · [지도/로컬 FAQ(길찾기 API 미제공)](https://devtalk.kakao.com/t/faq-api/60356)
- 공개 예제(오퍼레이션 이름 참고): [TrueETA docs/api-endpoints.md](https://github.com/Hun0305/TrueETA/blob/main/docs/api-endpoints.md) (GBIS v2) · [korea-metropolitan-bus-alerts api_reference.md](https://github.com/modbender/skill-library-mcp/blob/main/data/korea-metropolitan-bus-alerts/references/api_reference.md) (TAGO `getCrdntPrxmtSttnList`)

---

## 9. ❓ 사용자 PC 에서 확인할 것

| # | 무엇 | 어디서 · 어떻게 | 성공 기준 |
|---|---|---|---|
| ① | 재단 원문에 교통 문구가 있는지 | MySQL Workbench(또는 로컬 Claude 에 요청) → `SELECT id, title FROM event WHERE info LIKE '%교통%' OR info LIKE '%오시는%' OR info LIKE '%버스%' OR info LIKE '%지하철%';` | 0건이면 후보 3 제외 확정. 있으면 건수 · 라벨 이름을 PROGRESS 에 |
| ② | 경기도 정류소 API 키 · 동작 | ① [data.go.kr 경기도_정류소 조회](https://www.data.go.kr/data/15080666/openapi.do) → 로그인 → "활용신청"(자동승인) ② 마이페이지 → 인증키(Decoding) 복사 ③ 브라우저 주소창: `https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationAroundListv2?format=json&x=127.01439&y=37.28188&serviceKey=키` (화성행궁 광장 좌표) → **키가 든 주소는 캡처 · 공유하지 말 것** | `resultCode` 0 + `busStationList` 에 정류장 · `distance` 가 보이면 ✅. "SERVICE KEY IS NOT REGISTERED" 면 발급 뒤 1~2시간 기다렸다 다시 |
| ③ | 경유노선(버스 번호) | ②의 응답에서 `stationId` 하나 → `…/getBusStationViaRouteListv2?format=json&stationId=번호&serviceKey=키` | `routeName` 에 버스 번호가 나오면 ✅ |
| ④ | 활용기간 · 한도 | data.go.kr 마이페이지 → 개발계정 상세 | 활용기간(만료일) · 일 트래픽 1,000 을 PROGRESS 에 적기 |
| ⑤ | 카카오 로컬 결과 캐시 허용 | [카카오 개발자 운영정책 · 지도 이용약관](https://developers.kakao.com) 에서 "저장" · "캐시" 문구 | 하루 캐시 가능 여부 한 줄 |
| ⑥ | (선택) TAGO 에 수원 버스가 있는지 | data.go.kr TAGO 버스정류소정보 활용신청 → "도시코드 목록 조회" | 목록에 수원시가 있으면 예비 API 로 기록 |
