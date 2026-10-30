# CLAUDE.md — 수원 문화지도 (Spring Boot 버전)

> Claude Code와 claude.ai 채팅 세션이 **같이 읽는 공통 안내서**입니다.
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

## 5. 작업 방식 (두 세션 분담)

| 누가 | 하는 일 |
|---|---|
| **claude.ai 채팅** | 계획 · 코드 작성 → 브랜치 push → PR 생성 · 포트폴리오/자소서 문구 |
| **Claude Code (로컬 PC)** | PR 브랜치 pull → **빌드·실행·브라우저 확인** · 에러 수정 · push |

- **원본은 GitHub 하나.** 로컬에서만 고치고 끝내지 말 것 → 반드시 커밋·push
- 채팅 환경은 Maven 저장소 접속이 막혀 빌드가 안 됨 → **빌드·실행 검증은 Claude Code 담당**
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
