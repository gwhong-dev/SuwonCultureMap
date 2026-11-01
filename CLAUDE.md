# CLAUDE.md — 수원 문화지도 (Spring Boot 버전)

> Claude 작업 세션 · 검증 에이전트가 **같이 읽는 공통 안내서**입니다.
> 작업을 시작하기 전에 이 파일과 `PROGRESS.md`를 먼저 읽으세요.

## 1. 프로젝트 한 줄 요약

수원문화재단 공공 API(XML)의 행사 정보를 MySQL에 동기화하고, 카카오맵 위에 보여주는 웹 서비스.
1인 개발 · 취업 포트폴리오용 · "AI 워크플로우를 활용하는 풀스택 개발자"를 보여주는 것이 목표.

- 정적 배포 버전(로그인·DB 없음): https://gwhong-dev.github.io/suwon-culture-map/
- 이 저장소: 백엔드 포함 원본 (동기화 · 소셜 로그인 · 계정 통합 · 찜)

## 2. 기술 스택

- Java 17, Spring Boot 4, Maven (`./mvnw`)
- Spring Data JPA, MySQL, Jackson XML(`XmlMapper`), RestTemplate
- 프론트: `src/main/resources/static/` 의 HTML·CSS·JS (index / login / mypage) + Kakao Maps JS API
- 인증: 카카오·구글·네이버 OAuth 2.0 직접 구현, 세션(`HttpSession`) 기반

## 3. 폴더 구조

```
src/main/java/com/suwon/festival/
├── config/        RestTemplate 설정
├── controller/    Auth · Event · Favorite · Member · 예외 처리
├── dto/           API 응답 / XML 매핑 / 소셜 로그인 응답
├── entity/        Member · LoginMethod · Event · Favorite
├── repository/    JPA 리포지토리
└── service/       동기화 · 소셜 로그인 3종 · 찜 · 회원
src/main/resources/
├── static/        index.html · login.html · mypage.html
└── application*.properties
```

## 4. 설정 파일 규칙 (중요)

- 비밀값(DB 비밀번호, API 키, client-secret)은 **절대 커밋하지 않는다**
- 프로필을 지정하지 않으면 `dev` 로 실행됨 (`spring.profiles.default=dev`), 운영은 `SPRING_PROFILES_ACTIVE=prod`
- 개발/운영 설정 분리 (PR #8, 머지 완료) 구조:
  - `application.properties` — 공통, 비밀값 없음
  - `application-dev.properties` — 로컬 실제 값 (gitignore)
  - `application-prod.properties` — 값은 전부 환경변수 `${...}`

## 5. 작업 방식 (로컬 Claude Code + 검증 에이전트, 2026-11-01 전환)

> 작업 PC 1대 고정. Claude 가 그 PC 의 **로컬 Claude Code(데스크톱 앱 Code 탭)** 에서 빌드 · 실행 · 확인까지 직접 한다. 사용자는 꼭 필요할 때만 호출한다.

- 프로젝트 위치: `F:\dev\개인프로젝트\수원문화지도\dev\SuwonCultureMap`

| 누가 | 하는 일 |
|---|---|
| 🛠️ **작업 세션** (로컬 Claude Code) | 계획 · 코드 · 빌드 · 앱 실행 · DB 조회 · API 확인 · 브랜치 push · PR · 머지 |
| 🔍 **검증 에이전트** | M · L 등급 PR 만. 작업 내용을 **처음 보는 상태**로 독립 검토 → **PR 댓글 1개**로 기록 |
| 🙋 **사용자** | 소셜 로그인 실제 계정 클릭 + 머지 승인 |

흐름: 기능 브랜치 → 작업 · 로컬 확인(`mvn test`, 필요하면 앱 실행) → PR → (M · L) 검증 에이전트 → 지적 반영 → CI 통과 → 사용자 승인 → 머지

- **원본은 GitHub 하나.** 검증 기록도 PR 댓글에 남김
- **로컬 작업은 항상 기능 브랜치에서.** `main` 직접 수정 금지 (작업 시작 전 `git pull` 로 최신화)
- 비밀값 파일 `application-dev.properties` 는 **실행에만** 쓰고, 내용을 출력 · 커밋하지 않음 (키 이름만 언급)
- DB 조회 · API 확인도 Claude 가 직접. 결과를 보여줄 때 비밀값 · 개인정보는 가림
- ~~USB 반영 스크립트(`scripts/`) 규칙~~ **폐기** — 로컬에서 직접 하므로 "git pull 후 더블클릭" 용 스크립트를 새로 만들지 않음 (기존 스크립트는 그대로 둠)
- **CI(GitHub Actions)가 PR마다 빌드 · 테스트 · Docker 빌드를 자동 실행** → CI 통과가 머지 조건 (`main` 브랜치 보호)
- 테스트는 `test` 프로필(H2 메모리 DB + 가짜 키)로 실행 → MySQL · 비밀값 없이 돈다. 새 기능엔 테스트를 같이 추가
- 등급: **S** 추가 절차 없음 · **M** 검증 에이전트 1회 · **L** M + 사용자 확인(필요할 때만)
- 작업을 끝내면 `PROGRESS.md`의 체크박스와 "작업 로그"를 갱신

## 6. Git 규칙

- `main` 직접 커밋 금지 → `feat/*`, `fix/*`, `chore/*`, `docs/*` 브랜치 → PR → 머지
- 커밋 메시지: `feat:` 기능 · `fix:` 버그 · `chore:` 설정/빌드 · `docs:` 문서
- 한 PR = 한 가지 목적

## 7. 자주 쓰는 명령

```bash
./mvnw spring-boot:run          # 로컬 실행 → http://localhost:8080
./mvnw -DskipTests package      # jar 빌드 (target/*.jar)
./mvnw test                     # 테스트 (test 프로필, H2 — MySQL 불필요)
docker compose up --build       # Docker 로 앱 + MySQL 실행 (.env 는 scripts/setup-docker.cmd)
# Windows 로컬에서는 mvnw.cmd 사용
```

## 8. 사용자 정보 (설명 방식)

- 개발 입문자(기계설계 4년 경력 → 풀스택 전환 중). **답변·설명은 항상 한국어**
- 짧고 핵심 먼저, 비유 섞어서, 처음 나온 영어 용어는 한글로 풀이
- 에러는 "원인 한 줄 → 해결 → 이유" 순서로
