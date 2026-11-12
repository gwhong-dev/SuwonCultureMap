# 작업 PC 준비 · 이어받기 (PC A 집 게이밍 노트북 · PC B 삼성 노트북)

> 규칙 원본은 [CLAUDE.md](../CLAUDE.md) 2번. 이 문서는 새 PC 를 준비하는 순서와 다른 PC 에서 이어받는 순서만 정리.
> 경로는 PC 마다 다름 → 아래 `<프로젝트 폴더>` 는 각 PC 의 내장 디스크 위치 (USB 는 작업 폴더로 쓰지 않음).

## 1. 새 PC 준비 (PC 마다 1번)

| # | 할 일 | 확인 (되어 있나요?) | 안 되어 있으면 |
|---|---|---|---|
| 1 | Git · JDK 17 · Node.js · MySQL 8 설치 | `git --version` · `java -version` · `node --version` · `services.msc` 에 MySQL80 | winget 또는 공식 설치 파일 |
| 2 | **gh**(GitHub 명령줄 도구) 설치 | `C:\Program Files\GitHub CLI\gh.exe` 가 있음 | `winget install --id GitHub.cli -e` |
| 3 | gh 로그인 | `gh auth status` → `Logged in to github.com account gwhong-dev` | `gh auth login --hostname github.com --git-protocol https --web` → 코드 붙여 넣고 Authorize. **터미널에서 Enter 를 눌러야** 로그인이 저장됨 |
| 4 | 내장 디스크에 clone | `<프로젝트 폴더>\pom.xml` 이 있음 | `git clone https://github.com/gwhong-dev/SuwonCultureMap.git` |
| 5 | 비밀값 파일 | `src\main\resources\application-dev.properties` 가 있음 | 다른 PC 의 파일을 **저장소 밖으로**(USB 등) 복사. 없으면 `application-dev.properties.example` 을 복사해 값 채우기. 내용은 출력 · 커밋 금지 |
| 6 | `scripts\setup-dev.cmd` 실행 | 마지막에 `===== 끝 =====`, 키 목록만 보이고 값은 안 보임 | 오류 줄을 보고 그 단계만 다시 |
| 7 | MySQL 에 `suwon_culture_map` DB | `./mvnw spring-boot:run` 뒤 http://localhost:8080 이 열림 | DB 만들기 → 다시 실행. 확인 뒤 앱 종료 |
| 8 | Claude 데스크톱 앱 Code 탭에서 `<프로젝트 폴더>` 열기 | 새 세션 첫 화면에 **[세션 시작 점검]** 메시지 | 폴더를 다시 열고 새 세션 (훅은 세션 **시작 때만** 읽힘 — 이미 열린 세션엔 안 걸림) |

## 2. 이어받기 (다른 PC 에서 하던 것)

1. Code 탭에서 **새 세션** 시작 → [세션 시작 점검] 이 자동으로 알려 줌
   - 커밋 안 된 변경 · push 안 된 커밋 · main 뒤처짐 · 열린 PR(Draft 표시)
2. 열린 Draft PR 이 있으면 그 브랜치로 이어감: `git fetch` → `git switch <브랜치>`
3. 다 하면 Draft 해제(`gh pr ready N`) → CI → 머지 (등급별 절차는 CLAUDE.md 4번)

끝낼 때 (다른 PC 로 넘기기 전): 커밋 + push. 덜 끝났으면 `wip:` 커밋 + Draft PR → 다른 PC 를 켜면 바로 이어감

## 3. 이어받기 시험 기록 (전환 6단계)

| PC | 무엇을 | 결과 |
|---|---|---|
| PC A | 이 문서 작성 · PROGRESS 기록 → push → Draft PR 로 남김 | ✅ (11/12) |
| PC B | 1번 준비 → 새 세션 [세션 시작 점검] 에 이 Draft PR 이 보이는지 → 브랜치 이어받아 이 칸 채움 → Ready → 머지 | ⏳ |
