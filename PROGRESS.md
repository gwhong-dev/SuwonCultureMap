# PROGRESS.md — 진행 현황판

> 작업 세션 · 검증 에이전트 · 사용자가 **지금 어디까지 했는지** 맞추는 파일.
> 작업 하나 끝날 때마다 체크하고, 아래 "작업 로그"에 한 줄 추가.

## 🎯 최종 목표

로컬에서만 돌던 프로젝트를 **운영 수준으로 정리**해서 포트폴리오를 강화하고 입사 지원.

## ✅ 오늘(10/30) 범위

- [x] 1단계 설계 — Spring 프로필 분리 PR 생성 (#8)
- [x] 1단계 검증 — PR #8 로컬 실행 + 소셜 로그인 3종 + 계정 연결 확인 → 머지
- [x] 2단계 — Docker화 (`Dockerfile` + `docker-compose.yml`, 앱 + MySQL)
- [x] 3단계 — CI (GitHub Actions: PR마다 빌드 + 테스트) + 브랜치 보호
- [x] 포트폴리오에 1~3단계 "문제 → 해결" 추가 (노션: 운영 수준 정리 — 문제 → 해결)
- [ ] 자기소개서 작성
- [ ] 입사 지원 🚀

## ✨ 기능 추가

- [x] 지도 기본 위치 → 정조테마공연장 (PR #19)
- [ ] 행사 캘린더 저장 A안 — 종일 일정(제목·날짜·메모·원문 링크), 아이폰 `.ics` + 구글 캘린더 링크
- [ ] 캘린더 B안 — 동기화 때 `infoItem`/`infoContent`에서 장소·시간 분리 (모르면 null, 원문 보관)
- [ ] 인스타 게시글 연동 — 공식 API(Business Discovery) + AI로 캡션 정리 + 카카오 장소 검색 (크롤링 X)

## 🔜 다음 로드맵 (지원 후)

- [ ] 4단계 — AWS EC2 운영 배포 + 도메인 + HTTPS
- [ ] 5단계 — CD (main 머지 → 자동 배포)
- [ ] 6단계 — 운영·유지보수 (헬스체크, 로그, 장애 → Issue → 수정 기록)
- [ ] (선택) Jenkins로 같은 파이프라인 재구성해 비교

## 📝 작업 로그

| 날짜 | 누가 | 내용 |
|---|---|---|
| 10/26 | 채팅 | PR #8 — 개발/운영 프로필 분리, OAuth `localhost` 하드코딩 제거 (빌드 미검증) |
| 10/30 | 채팅 | `CLAUDE.md` · `PROGRESS.md` 추가 (두 세션 공통 안내서) |
| 10/30 | Claude Code | `scripts/setup-dev.cmd` 추가 — PR #8 머지 전 비밀값 파일 이름 변경·커밋 방지 자동화 |
| 10/30 | 작업 세션 | 작업 방식 변경 — 단일 세션 + 검증 에이전트 + PR 댓글 기록 (사용자 중계 없음) |
| 10/30 | 사용자 PC | PR #8 검증 — dev 프로필 적용, MySQL 기동, 로그인 3종·계정 연결 모두 ✅ → 머지 |
| 10/30 | 작업 세션 | 2단계 Docker — `Dockerfile`(2단계 빌드) · `docker-compose.yml`(앱+MySQL) · `scripts/setup-docker.cmd`(.env 자동 생성) PR #14 |
| 10/30 | 사용자 PC | PR #14 검증 — 빌드 87초, prod 프로필·MySQL 컨테이너 연결, 동기화 후 마커·사이드바, 로그인 3종·계정 연결 모두 ✅ → 머지 |
| 10/30 | 작업 세션 | 3단계 CI — GitHub Actions(빌드+테스트, Docker 빌드), test 프로필(H2), 테스트 5개(컨텍스트 로드 + 테이블 생성 확인 + 소셜 로그인 리다이렉트 3종) |
| 10/30 | 사용자 | `main` 브랜치 보호 설정 — CI 2개 통과 필수, 관리자 예외 없음 |
| 10/30 | 작업 세션 | 노션에 1~3단계 "문제 → 해결" 8건 정리 |
| 10/30 | 작업 세션 | 보안 — OAuth state(CSRF 방지) 3종 적용, 콜백 검증·1회용·취소 처리, 테스트 추가 |
| 10/30 | 작업 세션 | 포트폴리오 대표자료 v2(15장) 작성 — 리뷰에서 보류한 지적 3건 + 자체 발견 1건을 "나중에 할 일"에 추가 |
| 10/30 | 사용자 PC | 폰(같은 Wi-Fi) 접속 테스트 — dev 파일의 `kakao`·`google.redirect-uri` 줄이 `app.base-url`을 덮어써 콜백이 localhost로 감 → 실행 인자로 덮어써서 카카오 로그인 ✅ |
| 10/30 | 작업 세션 | 지도 기본 위치 → 정조테마공연장 (PR #19) — 검증 리뷰 ⚠️ 1건(테스트 비교 완화) 반영, CI 통과 → 머지 |

## ⚠️ 막힌 것 / 메모

- 클라우드엔 MySQL·비밀값이 없음 → DB가 필요한 실행·로그인 확인은 사용자 PC에서
- `git pull` 후 `scripts\setup-dev.cmd` 를 먼저 실행 (비밀값 파일 이름 변경·커밋 방지 자동 처리)
- Docker 실행: `scripts\setup-docker.cmd`(최초 1회) → `docker compose up --build`. 컨테이너 DB는 새 DB라 처음 한 번 **행사 동기화** 필요
- 폰 테스트: PC IP로 접속 + 카카오·네이버 콘솔에 IP 주소 등록. 구글은 사설 IP 불가. dev 파일의 `redirect-uri` 줄을 지우기 전엔 실행 인자로 `--kakao.redirect-uri=...` 를 같이 넘겨야 함 (구글은 사설 IP 등록 불가라 폰 테스트에서 제외 → 넘길 필요 없음)
- 새 PC에서 `dubious ownership` 오류 → `scripts\setup-dev.cmd` 1번 실행 (설정이 USB가 아니라 PC에 저장되므로 PC마다 1번)
- PC에서 `Connection refused` 가 나면 MySQL 서비스가 꺼진 것 → `services.msc` 에서 MySQL80 시작 (관리자 권한)

## 🧰 나중에 할 일 (발견 사항)

- [x] `mvnw` 실행 권한 없음(100644) → PR #13 으로 수정
- [x] 네이버 로그인 `state=test123` 고정값 → 카카오·구글·네이버 모두 무작위 state 생성·검증 (CSRF 방지)
- [ ] PR #8 머지 후 dev 파일의 `kakao.redirect-uri`·`google.redirect-uri` 줄 삭제 (`app.base-url`로 자동 생성되게)
- [ ] 네이버 토큰 요청 URL에 `code`를 인코딩 없이 붙임 (`NaverAuthService.java:29-34`) — state 검증 뒤라 위험 낮아 보류 (PR #17 리뷰)
- [ ] 개발 확인용 `/api/events/sync/raw` · `/sync/parsed` 정리, `POST /api/events/sync` 도 로그인 없이 호출 가능 — 운영 배포 전에 제거하거나 관리자 전용으로 (자체 발견 + PR #18 리뷰)
- [ ] prod `spring.jpa.hibernate.ddl-auto=update` → DB 마이그레이션 도구(Flyway 등) 도입 후 `validate` (PR #8 리뷰)
- [ ] `setup-docker.ps1` 의 properties 특수 표기(`\=` `\:` `\uXXXX`, 줄 끝 `\` 이어쓰기) 미처리 — 현재 dev 견본 형식엔 해당 없음 (PR #14 리뷰)
- [ ] 행사 제목에 `&#39;`가 그대로 보임 (예: `&#39;어울야락(夜樂)&#39;`, 폰 테스트 중 발견) — `title`은 이미 `htmlUnescape` 함(`EventSyncService.java:63`). 사용자 PC에서 **재동기화 후에도 남는지** 확인 → 남으면 원문 XML 인코딩 횟수(이중 이상) 확인 (PR #20 리뷰)
