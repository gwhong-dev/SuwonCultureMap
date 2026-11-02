# 수원 문화지도 프로젝트 지침 (로컬 Claude Code용)

## 1. 프로젝트

- 수원문화재단 공공 API(XML) 행사 정보를 MySQL에 동기화해 카카오맵에 보여주는 웹 서비스
- 1인 개발 · 취업 포트폴리오용. 목표: "AI 워크플로우를 활용하는 풀스택 개발자"를 보여주기
- 저장소
  - 원본(Spring Boot): gwhong-dev/SuwonCultureMap ← 주 작업 대상
  - 따라 만들기 연습: gwhong-dev/SuwonCultureMap-practice
  - 정적 배포: https://gwhong-dev.github.io/suwon-culture-map/
- 운영: https://suwonbible.com (AWS EC2 + Docker Compose, 절차는 `docs/deploy-ec2.md`)
- 작업을 시작하면 PROGRESS.md 상단 "현재 상태 · 다음 할 일"만 읽고 짧게 알릴 것
- 완료 로그(PROGRESS-LOG.md)는 요청할 때만 읽음

**기술 스택**

- Java 17, Spring Boot 4, Maven (`mvnw`), Spring Data JPA, Flyway, MySQL, Jackson XML, RestTemplate
- 프론트: `src/main/resources/static/` 의 HTML · CSS · JS + Kakao Maps JS API
- 인증: 카카오 · 구글 · 네이버 OAuth 2.0 직접 구현, 세션 기반

**폴더 구조**

```
src/main/java/com/suwon/festival/
├── config/  controller/  dto/  entity/  repository/  security/
└── service/   동기화 · 소셜 로그인 · 찜 · 회원 · 캘린더
    └── caption/  instagram/  place/  merge/  schedule/
src/main/resources/
├── static/        about · index · login · mypage · privacy.html, schedule-format.js
├── db/migration/  Flyway 스크립트 (V1~)
└── application*.properties
deploy/  docs/  scripts/
```

**자주 쓰는 명령** (Windows 는 `mvnw.cmd`)

```bash
./mvnw spring-boot:run      # 로컬 실행 → http://localhost:8080
./mvnw test                 # 테스트 (test 프로필, H2 — MySQL 불필요)
node --test src/test/js/schedule-format.test.js   # 화면 표시 규칙 시험
./mvnw -DskipTests package  # jar 빌드
docker compose up --build   # 앱 + MySQL (.env 는 scripts/setup-docker.cmd)
```

## 2. 작업 환경

- 작업 PC 1대 고정, 로컬 Claude Code에서 작업
- 프로젝트 위치: D:\dev\개인프로젝트\수원문화지도\dev\SuwonCultureMap
- 원본은 GitHub. PC 교체 대비는 저장소 문서(CLAUDE.md · PROGRESS.md)로 함
- MySQL · 비밀값이 PC에 있음 → 빌드 · 테스트 · 앱 실행 · DB 조회 · API 확인까지 Claude가 직접 수행
- 앱 실행 후 확인이 끝나면 띄운 프로세스는 종료해 둘 것

## 3. 작업 구조 (사용자는 중계하지 않음)

- Claude: 계획 · 코드 · 로컬 실행 검증 · 브랜치 push · PR · (승인 후) 머지
- 검증 에이전트(서브 에이전트): M · L 등급 PR에서만 호출
  - 작업 내용을 처음 보는 상태로 독립 검토
  - 항목 | 결과(✅⚠️❌) | 근거 파일:줄 | 조치 표(최대 8줄)로 PR 댓글 1개에 기록
  - 마지막 줄에 판정
- 사용자: 소셜 로그인 실제 계정 클릭, 머지 승인만
- 다른 세션에 시켜야 하는 예외가 생기면 따로 표시하고, 복붙용 프롬프트를 코드블록으로 줄 것

## 4. 개발 절차

1. PROGRESS.md 확인 → 등급(S/M/L) 판단 후 짧게 알림
2. 기능 브랜치 생성 (main 직접 수정 금지, 시작 전 `git pull`)
   - 브랜치 이름: `feat/*` · `fix/*` · `chore/*` · `docs/*`, 한 PR = 한 가지 목적
3. 커밋 접두어: feat: / fix: / refactor: / test: / chore: / docs:
4. 로컬에서 mvn test 통과 확인
5. push → PR 생성
6. CI 통과 확인 (빌드 + 테스트, Docker 이미지 빌드 — main 브랜치 보호로 필수)
7. 사용자가 "머지"라고 명시적으로 승인한 뒤에만 머지
   (단, 정할 것이 없는 작업은 CI 통과 후 자동 머지 가능 — 사전에 합의된 경우만)
8. PROGRESS.md 체크박스 갱신, 작업 로그는 PROGRESS-LOG.md에 기록

등급별 추가 절차

- S: 추가 없음 (검증 에이전트 · 반영 댓글 생략)
- M: 검증 에이전트 1회 → PR 댓글 1개
  - 지적 반영은 같은 댓글의 "조치" 열을 갱신 (별도 댓글 금지)
  - 보류 항목은 이유와 함께 PROGRESS.md "나중에 할 일"에 기록
- L: M 절차 + Claude가 PC에서 앱 실행 · 화면 · DB 확인
  - 결과는 ✅/❌ + 로그 요약으로 PR 댓글에 기록
  - 소셜 로그인 확인만 사용자에게 요청

- 새 기능에는 테스트를 같이 추가 (test 프로필 = H2 + 가짜 키, MySQL 없이 실행)
- 머지된 브랜치는 맨 나중에 정리 → 중간에 브랜치 삭제를 권하거나 진행하지 말 것
- "git pull 후 더블클릭"용 USB 반영 스크립트는 새로 만들지 않음 (기존 `scripts/` 는 그대로 둠)

## 5. 안전 규칙

- 비밀값(DB 비밀번호, API 키, client-secret)은 절대 커밋 · 출력하지 않음 (키 이름만)
- application-dev.properties는 실행에만 사용, 내용 출력 금지
- 설정: application.properties(공통, 비밀값 없음) / application-dev.properties(로컬, gitignore) / application-prod.properties(환경변수만)
- 프로필 미지정이면 `dev` 로 실행, 운영은 `SPRING_PROFILES_ACTIVE=prod`
- 실행 전 사용자 확인 필수 명령:
  - 파일 · 폴더 삭제 / 이동
  - git reset --hard, git push --force, git clean
  - DB 데이터 삭제 · 테이블 변경(DROP, TRUNCATE, DELETE 전체)
  - PC 전역 설정 · 환경변수 변경
- 위험한 작업(원본 정리 · 삭제 · 머지)은 검증 전에 하지 않음
- 수정 요청 순서: "점검 항목 먼저 → ❌ 확인 → 고쳐서 ✅"
- 원인은 표로 보여주고 고치기 전에 멈춤

## 6. 안내 방식

- 사용자: 개발 입문자 (기계설계 4년 경력 → 풀스택 전환 중)
- 답변은 항상 한국어
- 짧고 핵심 먼저, 표 · 굵게 · 이모지로 한눈에
- 처음 나온 영어 용어는 한글 풀이
- 비유 · 개념 설명은 생략, 코드와 해결책 위주
- 에러는 "원인 한 줄 → 해결 → 이유 한 줄" 순서
- 변경한 파일 / 부분만 보여줌, 전체 재출력 금지
- 모르는 건 추측하지 말고 물어볼 것
- 손으로 하는 준비 작업은 "직접 하세요"로 넘기지 말고 Claude가 직접 처리
- 사용자에게 시킬 때(소셜 로그인 등)는 단계별로:
  어디서 열고 → 무엇을 하고 → 어떤 결과면 성공인지
  - 각 단계에 "되어 있나요? → 안 되어 있으면 이렇게"

## 7. 포트폴리오 · 문서

- "문제 → 해결" 정리는 노션 "수원문화지" 페이지 하위. 비밀값은 비공개 페이지에만
- 포트폴리오 대표자료는 Claude Design 캔버스
  - 톤: AI 느낌 없이(폰트 · 색 · 둥근 모서리 최소)
  - 핵심: 검증 플로우와 시행착오 → 보완
- 자기소개서 톤: "잘했다 · 바로 기여하겠다"가 아니라
  "실무 개발 흐름을 이해하고 그 절차대로 개발했다"

## 8. 토큰 운영

- 작업 하나가 끝나면 새 세션에서 다음 작업 시작 (긴 대화 이어쓰기 지양)
- 노션 · Claude Design · 포트폴리오 작업은 코딩 세션과 분리
- PROGRESS.md 상단 "현재 상태 · 다음 할 일"은 20줄 안팎 유지, 그 아래는 막힌 것 · 나중에 할 일만. 완료 로그는 PROGRESS-LOG.md로
- 위험 작업(L 등급) 검증은 줄이지 않음 — 절약 대상은 S · M
- 새 세션 전환 (말로만 "넘어가세요" 금지):
  - 전환 시점: ① 한 PR/작업 목적이 끝났을 때 ② 대화가 길어졌을 때 ③ 주제가 바뀔 때
  - 먼저 정리: 커밋 · push 완료, PROGRESS.md 갱신, 미완료는 "나중에 할 일"에 기록
  - 정리 후 맨 끝에 "🔁 새 세션으로 넘어가세요" + 인수인계 코드블록 1개
  - 인수인계 형식 (15줄 이내, 비밀값 금지):
    첫 줄: [수원문화지도 인수인계] 저장소/브랜치/PR 번호
    1. 목표 2. 완료 3. 진행 중(막힌 부분) 4. 결정 사항 5. 변경 파일 6. 다음 할 일(한 가지)
       마지막 줄: "PROGRESS.md 상단만 읽고 6번부터 이어서 진행해줘"
