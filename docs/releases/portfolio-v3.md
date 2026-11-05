포트폴리오 제출용 **시점 고정 스냅샷**입니다. (2026.11.5 기준 · v3)
v2(2026.11.3, #123) 이후 PR #124~#150 을 반영했습니다. 사이트(https://suwonbible.com)는 계속 개선되며, 이후 변경은 다음 Release로 기록합니다.

📹 **당시 화면 영상**: 이 Release 첨부 파일(1분 화면 녹화) — 포트폴리오 캔버스 02장에서 안내하는 영상입니다.

## v2 이후 바뀐 것
- 모바일 홈 · 검색: 계획 문서 1개 → PR 8개로 나눠 진행 — 홈 뼈대(#134) · 포스터 캐러셀(#135) · 날짜 칩(#136) · 이달의 행사(#138) · 구 정보(#139) · 내 주변(#142) · 검색 화면(#145) · 검색 결과(#147)
- 찜: 찜 토스트 + 찜 탭 빈 상태(#126), 서버 찜만 유지 · 비로그인은 로그인 안내
- 데이터: 동기화 때 카카오 좌표 → 구(장안 · 권선 · 팔달 · 영통) 저장(Flyway V6, #139), 진행 날짜 — 합치기 대표 날짜(#124) · 인스타 하루짜리 · 여러 회차 · 기간 + 매주(#125)
- 화면 버그: 사진 없는 인스타 행사 목록 카드 빈 칸 제거(#132), 배포 직후 옛 JS 섞임 방지 `Cache-Control: no-cache`(#128)
- CI 비교: 같은 파이프라인을 Jenkins(내 PC Docker, CI 만)로 재구성(#149) → 첫 빌드 #1 SUCCESS · 3분 12초 · JUnit 533개 통과(#150, `docs/jenkins.md` 5번)

## 이 시점의 상태
- 운영: https://suwonbible.com — AWS EC2 + Docker Compose, main 머지 → CI → 자동 배포
- PR 150개 중 147개 머지, 테스트 `mvnw test` 533개 · `node --test` 93개

## 기술 스택
- Backend: Java 17, Spring Boot 4, Spring Data JPA, Flyway, Jackson XML, RestTemplate
- DB: MySQL 8.4 (테스트는 H2)
- Frontend: HTML · CSS · JavaScript, Kakao Maps JavaScript API
- 인증: 카카오 · 구글 · 네이버 OAuth 2.0 직접 구현, 세션 기반
- 인프라: GitHub Actions(CI/CD · 가동 감시), Jenkins(CI 비교), Docker Compose, AWS EC2, Caddy

문서: [README](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v3/README.md) · [Jenkins 비교](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v3/docs/jenkins.md) · [배포 절차](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v3/docs/deploy-ec2.md) · [v2](https://github.com/gwhong-dev/SuwonCultureMap/releases/tag/portfolio-v2) · [v1](https://github.com/gwhong-dev/SuwonCultureMap/releases/tag/portfolio-v1)
