# Jenkins 로 CI 재구성 · GitHub Actions 와 비교

> 작성일: 2026-11-05
> 목적: 지금 GitHub Actions CI(`.github/workflows/ci.yml`)와 **같은 단계**를 Jenkins(직접 설치해서 쓰는 CI 서버)로 만들어 보고 차이를 정리
> 범위: **CI 만**. 운영 배포는 지금처럼 GitHub Actions `Deploy` 가 담당 (Jenkins 에 AWS 권한을 주지 않음 — 두 곳에서 배포되는 일 방지)
> 실행 위치: **내 PC Docker** (비용 0원, 필요할 때만 켬)

## 1. 파일

| 파일 | 역할 |
|---|---|
| `Jenkinsfile` | 파이프라인 (단계 정의). Jenkins 가 GitHub 원본에서 읽음 |
| `jenkins/Dockerfile` | Jenkins(최신 LTS) + 빌드용 JDK 17 + Node 22 + Docker CLI · compose · buildx |
| `jenkins/plugins.txt` | 플러그인 목록 (이미지 만들 때 설치 → 설치 마법사 없음) |
| `jenkins/casc.yaml` | 설정 자동화(JCasC): 관리자 계정 · 작업 `SuwonCultureMap` 생성 |
| `jenkins/docker-compose.yml` | 포트 `127.0.0.1:8081` (앱 8080 과 안 겹침, 이 PC 안에서만), 볼륨 `jenkins-home`, `docker.sock` 연결 (root 실행 — 아래 주의) |
| `jenkins/.env` | 관리자 비밀번호 (start 스크립트가 무작위로 만듦, **Git 제외**) |
| `scripts/jenkins-start.cmd` · `.ps1` | 더블클릭 → Docker 확인 · 비밀번호 생성 · 빌드 · 실행 · 브라우저 열기 |
| `scripts/jenkins-stop.cmd` | 끄기 (설정 · 기록 · Maven 캐시는 남음) |

## 2. 켜기 · 빌드 (PC)

| 단계 | 어디서 · 무엇을 | 성공이면 | 안 되면 |
|---|---|---|---|
| ① Docker 켜기 | 시작 메뉴 → **Docker Desktop** 실행 | 작업 표시줄 고래 아이콘이 멈춤 | 설치가 안 돼 있으면 Docker Desktop 설치 |
| ② 최신 받기 | 프로젝트 폴더에서 `git pull` | `Jenkinsfile` · `jenkins\` 폴더가 생김 | 충돌이 나면 Claude 에게 |
| ③ Jenkins 켜기 | 탐색기 → `scripts\jenkins-start.cmd` 더블클릭 | 창에 `[성공] http://localhost:8081` + 아이디 · 비밀번호, 브라우저가 열림 (처음 3~5분) | 창의 `[오류]` 줄대로. 8081 을 다른 프로그램이 쓰면 그 프로그램 종료 |
| ④ 로그인 | 브라우저 → 아이디 `admin` · 창에 나온 비밀번호 | 대시보드에 `SuwonCultureMap` 작업 | 비밀번호는 `jenkins\.env` 에도 있음 |
| ⑤ 빌드 | `SuwonCultureMap` → **파라미터와 함께 빌드** → BRANCH `main` → 빌드 | 5칸(Checkout + 4단계) 모두 초록 · `Test Result` 에 테스트 개수 (첫 빌드 5~10분) | 빨간 단계 → **Console Output** 마지막 30줄을 Claude 에게 |
| ⑥ 끄기 | `scripts\jenkins-stop.cmd` | "Jenkins 를 껐습니다" | - |

- 켜져 있는 동안 5분마다 새 커밋이 있는지 확인해 자동 빌드 (PC 는 외부에서 접속할 수 없어 GitHub 웹훅 대신 폴링)
- 다른 브랜치(PR)도 ⑤에서 BRANCH 만 바꾸면 빌드. ⚠️ 폴링은 **마지막으로 빌드한 브랜치**를 봄 → 다른 브랜치를 빌드했으면 끝난 뒤 `main` 으로 한 번 다시 빌드
- ⚠️ 주의: Jenkins 컨테이너는 root + `docker.sock` 이라 사실상 **PC Docker 전체 권한**을 가짐 → 이 PC 안(127.0.0.1)에서만 열고, 믿을 수 있는 저장소(이 원본)만 빌드

## 3. 단계 대응

| GitHub Actions (`ci.yml`) | Jenkins (`Jenkinsfile`) | 차이 |
|---|---|---|
| 화면 표시 규칙 시험 (`node --test`) | 같음 | - |
| 컴파일 + 테스트 (`./mvnw verify`, Temurin 17) | 같음 (빌드용 JDK 17, Jenkins 본체는 JDK 21) | - |
| 테스트 결과 요약 (Python 으로 XML 집계 → PR 요약) | `junit` 단계 → **Test Result 화면 · 추이 그래프** | Jenkins 가 기본 기능으로 더 자세히 보여 줌 |
| 실패 시 리포트 업로드 | `archiveArtifacts` | 같음 |
| Docker 이미지 빌드 | 같음 (PC 의 Docker 사용) | - |
| 배포 파일 확인: compose 설정 · 스크립트 문법 · Caddyfile | 같음 (Caddyfile 은 표준 입력으로 넘김) | Jenkins 안에서 `-v $PWD` 가 PC 경로로 해석돼 방식만 바꿈 |
| 배포 파일 확인: **빈 MySQL 에서 앱 실행 · 백업 · 복원** | **넣지 않음** | PC 에서 운영 compose(포트 · 볼륨)를 띄우면 로컬 DB · 앱과 충돌 위험. GitHub Actions 에만 둠 |

## 4. GitHub Actions vs Jenkins

| 항목 | GitHub Actions (지금) | Jenkins (이번 구성) |
|---|---|---|
| 서버 | GitHub 가 준비 (설치 · 관리 없음) | 직접 설치 · 업데이트 · 플러그인 관리 |
| 비용 | 공개 저장소 무료 | PC 에서 0원 / 서버에 두면 서버 비용 |
| 실행 시점 | PR · push 때 바로 (웹훅), 새 push 가 오면 이전 실행 취소 | 5분 폴링 또는 수동 (PC 라 웹훅 못 받음), 새 빌드가 오면 이전 빌드 취소(`abortPrevious`) |
| PR 화면 연동 | 체크 결과가 PR 에 바로 표시, main 보호 규칙과 연결 | 이 구성은 PR 에 표시 안 함 (연동하려면 외부 접속 주소 + GitHub 플러그인 필요) |
| 설정 위치 | `.github/workflows/*.yml` | `Jenkinsfile` + 서버 설정(이번엔 `casc.yaml` 로 코드화) |
| 비밀값 | 저장소 Secrets · OIDC 임시 권한 | Jenkins Credentials (이번 CI 엔 비밀값 없음) |
| 캐시 | `setup-java` 의 `cache: maven` | `jenkins-home` 볼륨에 `.m2` 보관 |
| 테스트 결과 | 직접 만든 요약 한 줄 | 기본 Test Result · 실패 테스트 목록 · 추이 그래프 |
| 확장 | 마켓 Action | 플러그인 (많지만 버전 호환 관리 필요) |
| 맞는 곳 | GitHub 저장소 · 작은 팀 · 관리 인력 없음 | 사내망 · 사내 서버에 배포 · 오래 쓰는 빌드 서버가 있는 회사 |

**결론(이 프로젝트):** 저장소가 GitHub 에 있고 1인 운영이라 **GitHub Actions 유지**. Jenkins 는 같은 파이프라인을 코드(`Jenkinsfile` · JCasC)로 재현할 수 있는지 확인하는 용도로 둠.

## 5. 실행 기록

| 날짜 | 브랜치 · 커밋 | 결과 | 걸린 시간 | 메모 |
|---|---|---|---|---|
| 2026-11-05 12:15 (빌드 #1) | `main` `580a3ba` (#149 머지) · 폴링(SCM change)으로 시작 | ✅ SUCCESS — JUnit 533개 전부 통과(실행 47초), 단계: 화면 표시 규칙 시험 1.3초 · 컴파일+테스트 1분 29초 · Docker 이미지 빌드 1분 22초 · 배포 파일 확인 10초 | 3분 12초 | 학원 PC · 첫 빌드 · 공용 PC라 실행 후 `down -v` 로 정리. 패키지별 테스트: service 111 · service.instagram 81 · controller 73 · service.schedule 72 · service.caption 68 · service.merge 54 · service.place 42 · security 23 · repository 7 · config 1 · (root) 1. JS `node --test` 93개는 Jenkins JUnit 집계에 안 들어감 |
