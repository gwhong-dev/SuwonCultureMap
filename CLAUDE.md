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
- 개발/운영 설정 분리는 PR #8에서 진행 중 → 머지 후 구조:
  - `application.properties` — 공통, 비밀값 없음
  - `application-dev.properties` — 로컬 실제 값 (gitignore)
  - `application-prod.properties` — 값은 전부 환경변수 `${...}`

## 5. 작업 방식 (단일 세션 + 검증 에이전트)

> 사용자가 세션 사이를 복붙으로 중계하지 않는다. Claude끼리 견제·검증하고, 사용자는 꼭 필요할 때만 호출한다.

| 누가 | 하는 일 |
|---|---|
| 🛠️ **작업 세션** (claude.ai 클라우드 세션) | 계획 · 코드 작성 · 브랜치 push · PR 생성 · 머지 |
| 🔍 **검증 에이전트** | 작업 내용을 **처음 보는 상태**로 독립 검토 → 결과를 **PR 댓글**로 기록 |
| 🙋 **사용자** | PC에서만 가능한 일(MySQL·비밀값이 필요한 앱 실행, 소셜 로그인 클릭) + 머지 승인 |

흐름: 작업 → PR → 검증 에이전트 리뷰(PR 댓글) → 지적사항 반영 → (필요할 때만) 사용자 PC 확인 → 사용자 승인 → 머지

- **원본은 GitHub 하나.** 검증 기록도 PR 댓글에 남겨서 어느 PC에서든 확인 가능하게
- 클라우드 세션은 USB 파일을 직접 못 건드림 → USB 반영이 필요하면 `scripts/`에 스크립트를 넣고 `git pull` 후 더블클릭 1번으로 끝나게
- 클라우드에는 MySQL·비밀값이 없음 → DB가 필요한 실행·테스트는 사용자 PC에서
- 클라우드 세션마다 Maven 저장소 접속이 막혀 있을 수 있음 → 컴파일이 안 되면 "컴파일 미검증"으로 PR에 명시하고 사용자 PC 실행 단계에서 확인
- 작업을 끝내면 `PROGRESS.md`의 체크박스와 "작업 로그"를 갱신

## 6. Git 규칙

- `main` 직접 커밋 금지 → `feat/*`, `fix/*`, `chore/*`, `docs/*` 브랜치 → PR → 머지
- 커밋 메시지: `feat:` 기능 · `fix:` 버그 · `chore:` 설정/빌드 · `docs:` 문서
- 한 PR = 한 가지 목적

## 7. 자주 쓰는 명령

```bash
./mvnw spring-boot:run          # 로컬 실행 → http://localhost:8080
./mvnw -DskipTests package      # jar 빌드 (target/*.jar)
./mvnw test                     # 테스트
```

## 8. 사용자 정보 (설명 방식)

- 개발 입문자(기계설계 4년 경력 → 풀스택 전환 중). **답변·설명은 항상 한국어**
- 짧고 핵심 먼저, 비유 섞어서, 처음 나온 영어 용어는 한글로 풀이
- 에러는 "원인 한 줄 → 해결 → 이유" 순서로
