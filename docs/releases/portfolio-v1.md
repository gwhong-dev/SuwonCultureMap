포트폴리오 제출용 **시점 고정 스냅샷**입니다. (2026.11.2 기준 · v1, PR #97 머지 시점)
사이트(https://suwonbible.com)는 계속 개선되며, 이후 변경은 다음 Release로 기록합니다.

## 이 시점의 상태
- 운영: https://suwonbible.com — AWS EC2 + Docker Compose
- 자동 배포(CD): main 머지 → CI(빌드 · 테스트 · Docker 이미지) 통과 → 자동 배포
- 보안: OAuth state 검증 · 세션 쿠키 설정 · 포트 제한 · 운영 수동 API 제거 · 보안 헤더 6종(운영 HSTS)
- 가동 감시: 15분마다 헬스체크, 장애 시 `uptime` 라벨 Issue 자동 생성 · 복구 시 자동 닫기 (#96)
- 모바일 앱식 레이아웃 1단계: 하단 탭 · 상태 배지/D-day · 마커 카드 · 길찾기 창 · 상세 · 스켈레톤 (#84~#91)

## 기술 스택
- Backend: Java 17, Spring Boot 4, Spring Data JPA, Flyway, Jackson XML, RestTemplate
- DB: MySQL (테스트는 H2)
- Frontend: HTML · CSS · JavaScript, Kakao Maps JavaScript API
- 인증: 카카오 · 구글 · 네이버 OAuth 2.0 직접 구현, 세션 기반
- 인프라: GitHub Actions(CI/CD), Docker Compose, AWS EC2

## 개발 방식
- 한 PR = 한 가지 목적, 브랜치 → PR → CI 통과 후 머지 (main 브랜치 보호)
- 작업 크기(S/M/L)에 따라 검증 에이전트가 독립 검토 → PR 댓글 1개로 기록 → 지적 반영

문서: [README](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v1/README.md) · [배포 절차](https://github.com/gwhong-dev/SuwonCultureMap/blob/portfolio-v1/docs/deploy-ec2.md)
