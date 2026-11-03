포트폴리오 제출용 **시점 고정 스냅샷**입니다. (2026.11.3 기준 · v2)
v1(2026.11.2, #97) 이후 PR #98~#121 을 반영했습니다. 사이트(https://suwonbible.com)는 계속 개선되며, 이후 변경은 다음 Release로 기록합니다.

## v1 이후 바뀐 것
- 보안: CSP 를 단계적으로 적용 — 인라인 스크립트 분리 → `script-src`(#104), CSS 분리 → `style-src`(#109 · #113), 카카오 공유 SDK SRI(#114), 네이버 토큰 요청 인코딩(#112)
- 화면: 아이폰 1차 확인 5건 보완(#99), 카카오톡 공유 init 순서 버그 수정(#106), 인스타 행사 상세에 장소 · 시간 · 출처 표시(#119)
- 데이터: 재단 이미지 주소 https 저장(#116) → 운영 https 118 · http 0 확인(#121)
- 운영: 앱 로그를 볼륨에 날짜별 14일 보관(#107). 15분 가동 감시(#96) 이후 11/3까지 장애 Issue 0건
- 인스타 수집: 공식 5계정만 재개 — 백업 · 삭제 승인 → 켜기(#118) → 첫 수집 표시 24건(#121)
- 개발 방식: 머지는 CI 전부 통과(+ M · L 등급 검증 판정) 뒤 작업 AI 가 실행, 사람은 실계정 로그인 확인 · 보류 결정(#100 · #102)

## 이 시점의 상태
- 운영: https://suwonbible.com — AWS EC2 + Docker Compose, main 머지 → CI → 자동 배포
- PR 121개 중 119개 머지, 테스트 `mvnw test` 475개 · `node --test` 50개

## 기술 스택
- Backend: Java 17, Spring Boot 4, Spring Data JPA, Flyway, Jackson XML, RestTemplate
- DB: MySQL 8.4 (테스트는 H2)
- Frontend: HTML · CSS · JavaScript, Kakao Maps JavaScript API
- 인증: 카카오 · 구글 · 네이버 OAuth 2.0 직접 구현, 세션 기반
- 인프라: GitHub Actions(CI/CD · 가동 감시), Docker Compose, AWS EC2, Caddy

문서: [README](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v2/README.md) · [배포 절차](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v2/docs/deploy-ec2.md) · [v1](https://github.com/gwhong-dev/SuwonCultureMap/releases/tag/portfolio-v1)
