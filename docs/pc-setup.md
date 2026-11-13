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
| 9 | **USB 에만 있던 파일 4개** (GitHub 에 없음, 11/12 점검) | `<프로젝트 폴더>` 에 `.env` · `.dev\`(구글 client_secret json) · `docs\mobile-design-v1.md` · `docs\design-source.zip` 가 있고, `git status` 에 안 보임(gitignore) | USB 를 꽂고 옛 저장소 폴더(`USB:\dev\개인프로젝트\수원문화지도\dev\SuwonCultureMap`)에서 **같은 경로로 복사** — Code 에 "USB 파일 4개 복사해 줘" 라고 하면 됨. `.env` · `.dev\` 는 비밀값 → 내용 출력 · 커밋 금지 |

USB 파일 4개 복사 현황 (두 PC 다 끝나야 USB 옛 저장소 폴더를 `_old_SuwonCultureMap` 으로 바꾸고 2주 뒤 삭제 — 삭제는 사용자)

| PC | 상태 |
|---|---|
| PC A (집 게이밍 노트북) | ✅ 11/12 복사 · 원본과 같은지 확인 |
| PC B (삼성 노트북) | ✅ 11/13 확인 — 4개 모두 원본과 같음. USB 옛 저장소의 남은 초안(stash · 미커밋 워크트리)은 `.dev/usb-backup-2026-11-13/` 에 백업. 11/13 부터 밖에서는 아래 2-1 원격 조작으로 PC A 세션을 씀 |

## 2. 이어받기 (다른 PC 에서 하던 것)

1. Code 탭에서 **새 세션** 시작 → [세션 시작 점검] 이 자동으로 알려 줌
   - 커밋 안 된 변경 · push 안 된 커밋 · main 뒤처짐 · 열린 PR(Draft 표시)
2. 열린 Draft PR 이 있으면 그 브랜치로 이어감: `git fetch` → `git switch <브랜치>`
3. 다 하면 Draft 해제(`gh pr ready N`) → CI → 머지 (등급별 절차는 CLAUDE.md 4번)

끝낼 때 (다른 PC 로 넘기기 전): 커밋 + push. 덜 끝났으면 `wip:` 커밋 + Draft PR → 다른 PC 를 켜면 바로 이어감

## 2-1. 원격 조작으로 이어가기 (PC B → PC A, 11/13 사용자 결정)

밖에서는 PC B 에 작업 환경을 새로 열지 않고, 집 PC A 의 Code 탭 세션을 **Remote Control(원격 제어)** 로 조작한다 (PC B 브라우저 claude.ai/code 또는 Claude 앱).

| 할 일 | 확인 (되어 있나요?) | 안 되어 있으면 |
|---|---|---|
| PC A 를 켜 둠 | 전원 연결 · 덮개 열림 · Claude 앱 켜짐 | 덮개를 닫거나 절전에 들어가면 세션이 멈춤 (앱의 잠들기 방지는 덮개가 닫히면 효과 없음) |
| 조작할 세션의 Remote Control 켜기 | PC A 세션 도구줄 스위치 켜짐 → claude.ai/code 목록에 그 세션이 보임 | PC A 앞에서 스위치를 켜거나, 그 세션에 "원격 켜 줘" |
| 지시 · 승인은 **그 메인 세션에 직접** 입력 | — | 다른 세션을 거쳐 전한 말은 L 머지 · PR 닫기 같은 승인으로 쓰지 않음 (11/13 #233 · #239 에서 이 때문에 두 번 멈춤) |

- 원격 조작 중인 세션은 새 세션(분기)을 시작하지 못함 → 메인이 직접 하거나, PC A 앞에서 시작
- 정해진 시각 작업(예: "07:00 에 머지")은 실행 승인 창이 늦게 눌리면 그만큼 늦게 시작됨. 11/13 07:00 예약은 07:58 에 승인돼 다음날 07:00 으로 잡혔고 → 대기를 멈추고 바로 머지. 예약은 승인된 순간 시각을 다시 확인하고, 이미 지났으면 바로 실행

## 3. 이어받기 시험 기록 (전환 6단계)

| PC | 무엇을 | 결과 |
|---|---|---|
| PC A | 이 문서 작성 · PROGRESS 기록 → push → Draft PR 로 남김 | ✅ (11/12) |
| PC B | 1번 준비 → 새 세션 [세션 시작 점검] 에 이 Draft PR 이 보이는지 → 브랜치 이어받아 이 칸 채움 → Ready → 머지 | ✅ (11/12) 1번 1~8 확인(내장 디스크 clone · gh 로그인 · 비밀값 파일 gitignore · 로컬 앱 실행 → 행사 125건 조회), 새 세션 점검에 이 Draft PR 표시 → `git switch` 로 이어받음 |

> 참고: Code 탭 세션이 worktree(작업 복사본, `.claude/worktrees/…`) 에서 열리면 비밀값 파일은 원래 `<프로젝트 폴더>` 에만 있음 → 앱 실행 확인은 원래 폴더에서 한다 (비밀값을 worktree 로 복사하지 않음).
