# 브라우저 화면 시험 (Playwright)

`node --test` 는 화면 규칙 함수와 소스 글자를 비교하는 시험이라, 실제로 눌렀을 때 창이 열리는지 · 초점이 어디로 가는지는 못 본다.
이 시험은 진짜 브라우저(Chromium)로 앱을 열어 누르고 키를 쳐서 확인한다. (11/13 도입, 결정 = 아래 "고른 방식")

## 실행

```bash
npm install                        # 처음 한 번 (@playwright/test)
npx playwright install chromium    # 처음 한 번 (시험용 브라우저 내려받기)
npm run test:e2e                   # = npx playwright test
```

- 앱은 Playwright 가 직접 띄운다: `mvnw spring-boot:run` · **test 프로필**(H2 메모리 DB · 가짜 키) · 포트 **18080** (`E2E_PORT` 로 바꿈). 시험이 끝나면 앱도 끈다
  - 8080 에 dev 앱이 떠 있어도 부딪치지 않음. 18080 에 이미 뭔가 떠 있으면 시작하지 않고 실패한다 (`reuseExistingServer: false` — 다른 프로필 앱을 잘못 쓰지 않게)
- 첫 실행은 컴파일 때문에 1분 안팎, 그 뒤는 20초 안팎 (시험 4개)
- 실패하면 `test-results/<시험 이름>/` 에 화면 구조(`error-context.md`) · 기록(`trace.zip`)이 남는다 → `npx playwright show-trace <trace.zip>` 으로 단계별 화면 보기
- 시험 하나만: `npx playwright test -g "길찾기"`
- 앱이 안 떠서 시간 초과(240초)로 끝나면: 앱 로그는 기본으로 숨겨 두므로 `E2E_SERVER_LOG=1 npx playwright test` (PowerShell 은 `$env:E2E_SERVER_LOG=1; npx playwright test`) 로 다시 돌려 시작 오류를 본다

## 구조

| 파일 | 하는 일 |
|---|---|
| `playwright.config.js` | 앱 띄우기 · 포트 · 시간 제한 · 움직임 줄이기(첫 진입 애니메이션 끔) |
| `src/test/e2e/support.js` | 외부 주소 전부 막기 · 가짜 카카오 SDK · 가짜 행사 3건 · 스크립트 오류가 나면 실패 |
| `src/test/e2e/fake-kakao.js` | 카카오 지도 SDK 대신 쓰는 가짜 (화면 코드가 쓰는 만큼만) |
| `src/test/e2e/screen.spec.js` | 시험 4개 |

**네트워크에 기대지 않음**
- `localhost` 말고는 전부 막는다. 카카오 지도 로더(`dapi.kakao.com`)만 `fake-kakao.js` 로 바꿔 준다
- 카카오 공유 SDK(`t1.kakaocdn.net`)는 `index.html` 의 `integrity`(파일 지문 검사) 때문에 가짜를 넣을 수 없어 막고, 가짜 지도 로더가 `window.Kakao` 도 같이 만든다
- `/api/events` 는 시험 안에서 고정 3건(오늘 기준 진행 중)으로 바꾼다 — test 프로필 DB 는 비어 있고, 날짜가 지나도 같은 화면이 나오게
- `/api/events/{id}/transit` 은 서버가 경기도 · 카카오 API 를 부르므로 빈 결과로 바꾼다

**화면 스크립트 오류 = 실패**: 누르는 동작은 통과해도 그리기가 깨졌으면 잡도록, 시험마다 브라우저 콘솔 오류 · 페이지 오류가 하나라도 있으면 실패 (막은 외부 주소의 불러오기 실패는 뺌)

## 지금 있는 시험 (4개)

| 시험 | 화면 | 확인하는 것 |
|---|---|---|
| ☰ 메뉴 | PC 1280×800 | 열면 `aria-expanded=true` · 첫 줄(서비스 안내)로 초점 → Esc 로 닫히고 ☰ 로 초점 → 다시 열고 바깥 누르면 닫힘 |
| 길찾기 작은 창 | PC | 상세 칸 [길찾기] → 창이 화면 안에 열림 · 주소 · 카카오 / 네이버 링크 · [주소 복사] → × 로 닫으면 단추로 초점, Esc 도 같음 |
| 행사 상세 | 모바일 390×844 | 하단 탭 [지도] → 카드 줄 3장 → 카드 누르면 상세 창(같은 제목) → 뒤로 단추로 닫힘 |
| 카드 키보드 | 모바일 | 지도 탭 단추에서 Tab 만 눌러 카드에 닿음 · 다음 Tab 은 다음 카드 · Enter 로 그 행사 상세 |

시험을 만든 뒤 카드의 Enter 처리를 일부러 지워 돌려 보면 "카드 키보드" 시험이 실패하는 것을 확인했다 (진짜 고장을 잡는지).

## 시험을 더할 때

- 화면이 새 카카오 기능(예: `kakao.maps.Polyline`)을 쓰기 시작하면 `fake-kakao.js` 에도 같은 이름을 더해야 한다 — 빠지면 "화면 스크립트 오류" 로 실패하므로 바로 보인다
- 새 외부 API 를 부르는 화면이면 `support.js` 의 `prepare` 에서 `page.route` 로 가짜 응답을 준다
- 모바일 첫 화면은 홈 탭이다. 지도 · 카드 줄 시험은 `openMobileMap(page)` 로 시작

## CI

아직 CI(`.github/`)에는 넣지 않았다 — `.github/` 는 L 등급이라 따로 PR (PROGRESS.md 나중에 할 일).

## 고른 방식 (11/13)

- **앱을 진짜로 띄우고 행사 API 만 가짜로**: 화면 파일 · 보안 헤더(CSP — 인라인 스크립트 · style 속성 차단)까지 운영과 같은 조건에서 돌린다. 정적 파일만 따로 띄우는 방식은 CSP 를 못 보고, DB 에 시험 행사를 넣는 방식은 날짜가 지나면 결과가 바뀌어 버림
- **`spring-boot:run` + `spring.config.additional-location=src/test/resources/`**: test 프로필 설정이 시험 폴더에만 있어서. `-Dspring-boot.run.useTestClasspath=true` 로 해 봤을 때는 test 설정이 읽히지 않아 MySQL 드라이버로 H2 주소를 열다 앱이 뜨지 않았음(11/13) → 설정 폴더를 직접 알려 주는 쪽으로
