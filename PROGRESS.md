# PROGRESS.md — 진행 현황판

> 두 세션(claude.ai 채팅 · Claude Code)이 **지금 어디까지 했는지** 맞추는 파일.
> 작업 하나 끝날 때마다 체크하고, 아래 "작업 로그"에 한 줄 추가.

## 🎯 최종 목표

로컬에서만 돌던 프로젝트를 **운영 수준으로 정리**해서 포트폴리오를 강화하고 입사 지원.

## ✅ 오늘(10/30) 범위

- [x] 1단계 설계 — Spring 프로필 분리 PR 생성 (#8)
- [ ] 1단계 검증 — PR #8 로컬 실행 + 소셜 로그인 3종 확인 → 머지
- [ ] 2단계 — Docker화 (`Dockerfile` + `docker-compose.yml`, 앱 + MySQL)
- [ ] 3단계 — CI (GitHub Actions: PR마다 빌드 + 테스트)
- [ ] 포트폴리오에 1~3단계 "문제 → 해결" 추가
- [ ] 자기소개서 작성
- [ ] 입사 지원 🚀

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

## ⚠️ 막힌 것 / 메모

- 채팅 환경에선 Maven 빌드 불가 → 빌드·실행 확인은 Claude Code에서
- PR #8 머지 전 로컬 준비: 기존 `application.properties` → `application-dev.properties` 로 이름 변경 + `app.base-url=http://localhost:8080` 추가
- git pull 후 `scripts\setup-dev.cmd` 를 먼저 실행
