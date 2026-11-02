# AWS EC2 배포 절차

구성: 인터넷 → **Caddy**(HTTPS) → **앱**(Spring Boot) → **MySQL**. 셋 다 EC2 서버 1대의 Docker 컨테이너.

- 외부에 여는 포트: 80 · 443(웹), 22(관리 접속, 내 IP 만)
- 앱 8080 · DB 3307 은 서버 안에서만 접속 가능
- 동기화는 앱이 자동 실행 (매일 06시 · 18시, 재단 행사가 없는 새 DB 는 시작 직후)

## 1. 서버 만들기 (AWS 콘솔, 서울 리전)

| 항목 | 값 |
|---|---|
| 이름 | `suwon-app` |
| 이미지 | Ubuntu Server 24.04 LTS, 64비트(Arm) |
| 인스턴스 유형 | `t4g.small` (2GB) |
| 크레딧 사양 | **Standard** (Unlimited 는 추가 과금될 수 있음) |
| 종료 방지 | **활성화** (실수로 지우면 DB 와 서버 안 백업이 함께 사라짐) |
| 키 페어 | 새로 만들어 `.pem` 을 내 PC 에 보관 |
| 저장 공간 | 20GB gp3 |
| 보안 그룹 | SSH(22): 내 IP, HTTP(80) · HTTPS(443): 전체 |

서버를 만든 뒤 **탄력적 IP** 를 하나 받아 연결한다. 재시작해도 주소가 바뀌지 않게 하기 위함.

**접속** (내 IP 가 바뀌어 접속이 안 되면 보안 그룹의 SSH 를 현재 IP 로 고친다)

```powershell
icacls 키파일.pem /inheritance:r /grant:r "${env:USERNAME}:R"
ssh -i 키파일.pem ubuntu@탄력적IP
```

**비용:** 서버 · 디스크 · 공인 IP 합계가 월 약 20달러로 추정되어(공식 단가 미확인) 크레딧 100달러로는 182일을 못 버틸 수 있다. 크레딧이 먼저 바닥나도 Free 플랜이 끝나 서버가 멈춘다. 서버를 만들 때 단가를 확인하고, 잔액은 월 1회 EC2 대시보드에서 확인하며, 추가 크레딧 활동도 해 둔다. 예산 경보(월 20달러)는 설정 완료.

## 2. 도메인 연결 (Cloudflare → DNS)

| 유형 | 이름 | 값 | 프록시 |
|---|---|---|---|
| A | `@` | 탄력적 IP | DNS 전용(회색 구름) |
| A | `www` | 탄력적 IP | DNS 전용(회색 구름) |

- 프록시를 끄는 이유: HTTPS 인증서는 Caddy 가 직접 발급 · 갱신한다. 주황 구름이면 인증서 발급이나 접속이 실패할 수 있으니 먼저 회색인지 확인한다
- 메일 전달용 MX · SPF(TXT) 레코드는 건드리지 않는다

## 3. 처리방침 먼저 완성 (PR)

`privacy.html` 은 이미지에 포함되므로 **서버를 띄우기 전에 PR 로 입력을 끝낸다.** 서버에서 직접 고치지 않는다.

| 칸 | 내용 |
|---|---|
| 호스팅 | AWS, 대한민국 서울 |
| 문의 메일 | Cloudflare(전달), Google Gmail(수신). 국외 이전 고지 해당 여부 판단 |
| 접속 기록 | 7번의 Caddy 로그 확인 결과대로 |
| 백업 | 서버 14일 보관, PC 사본은 최신 1개만 유지 |
| 안전 조치 | 실제 적용한 것만 |
| 시행일 | 공개하는 날 |

## 4. 서버 준비

```bash
git clone https://github.com/gwhong-dev/SuwonCultureMap.git
cd SuwonCultureMap
sudo bash deploy/setup-server.sh
exit
```

다시 접속한다 (docker 권한 반영).

## 5. 비밀값 파일 `.env`

`.env.example` 을 복사해 값을 채운다. Git 에 올리지 않는다.

```bash
cp .env.example .env
chmod 600 .env
openssl rand -hex 24   # DB_PASSWORD, MYSQL_ROOT_PASSWORD 에 각각 새로 만들어 넣기
```

```
SITE_DOMAIN=suwonbible.com
APP_BASE_URL=https://suwonbible.com
SESSION_COOKIE_SECURE=true
COMPOSE_PROJECT_NAME=suwon
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

- `APP_BASE_URL` 을 localhost 그대로 두면 소셜 로그인이 실패한다
- `KAKAO_ADMIN_KEY` 는 운영에서 필수 (탈퇴 때 카카오 연결 해제)
- `COMPOSE_FILE` · `COMPOSE_PROJECT_NAME` 덕분에 이후 `docker compose` 만 입력하면 된다. 폴더 이름이 바뀌어도 같은 DB 를 쓴다
- 비밀번호에는 특수문자를 쓰지 않는다 (`$` 등을 compose 가 다르게 해석)
- DB 비밀번호는 첫 실행 때 DB 에 저장된다. 나중에 `.env` 만 고치면 접속이 실패한다. `docker compose down -v` 는 **DB 를 통째로 삭제**하므로 운영에서 쓰지 않는다
- 인스타 수집은 중단 상태다 (`INSTAGRAM_SYNC_ENABLED=false`, [결정 문서](decisions/2026-11-02-instagram-scope-off.md)). 다시 켜는 조건이 충족되기 전에는 `true` 로 바꾸지 않는다

## 6. 소셜 로그인 · 지도 콘솔

| 콘솔 | 할 일 |
|---|---|
| 공통 | 콜백 등록: `https://suwonbible.com/api/auth/{kakao\|google\|naver}/callback` |
| 카카오 | 지도 JS 키의 웹 도메인에 `https://suwonbible.com` 등록 |
| 구글 | 게시 상태를 프로덕션으로, 승인된 도메인 · 홈페이지 · 처리방침 URL 입력 |
| 네이버 | 서비스 URL 변경, 앱이 개발 중 상태면 공개 절차 확인 |

구글 "테스트" · 네이버 "개발 중" 상태에서는 등록된 계정만 로그인된다.

## 7. 실행과 공개 전 확인

```bash
docker compose up -d --build
docker compose logs -f app
```

첫 실행 로그에서 `재단 동기화` · `자동 동기화 완료` 를 확인한다. `일부 실패` 가 나오면 원인을 확인한다. 재배포 때는 이 로그가 다음 예약 시각까지 나오지 않는다.

- [ ] `https://suwonbible.com` 접속, `http://` · `www` 는 본 주소로 이동
- [ ] 처리방침에 `배포 전 입력` 문구 없음
- [ ] `POST /api/events/sync` 가 200 이 아님 (운영에는 수동 API 없음)
- [ ] 외부에서 3307 · 8080 접속 불가
- [ ] 지도 · 행사 이미지 표시 (브라우저 콘솔에 혼합 콘텐츠 차단 없음)
- [ ] 로그인 3종 · 찜 · 탈퇴, **테스터로 등록하지 않은 계정**으로도 로그인
- [ ] `bash deploy/backup-db.sh` 후 `~/backups` 에 파일 생성, 아래 절차로 복원까지 한 번 시험 (회원이 생기기 전)
- [ ] `docker stats --no-stream` · `free -h` · `df -h` 로 메모리 · 디스크 여유 확인. 메모리가 부족하면 DB 에 `--performance-schema=OFF` 를 먼저 시도
- [ ] `docker compose stop app` 후 사이트 요청 → `docker compose logs caddy` 에 방문자 IP 가 남는지 확인 → 결과를 처리방침에 반영 → `docker compose start app`

## 운영

**코드 갱신** — 보통은 아래 "자동 배포"가 한다. 수동으로 할 때 (빌드 중 응답이 느려질 수 있음):

```bash
bash deploy/deploy.sh   # git pull → 다시 빌드 · 실행 → 응답 확인 → 옛 이미지 정리
```

## 자동 배포 (CD)

main 에 머지 → CI 통과 → GitHub Actions `Deploy` → AWS SSM(원격 명령) → 서버에서 `deploy/deploy.sh`.

- 서버 22번 포트를 GitHub 에 열지 않는다. SSM 은 서버가 AWS 쪽으로 먼저 연결해 명령을 받아 오는 방식
- GitHub 에 AWS 키 · SSH 키를 저장하지 않는다. Actions 가 실행될 때마다 OIDC(GitHub 이 발급하는 1회용 신분증)로 15분~1시간짜리 임시 권한을 받는다
- 권한은 "이 저장소의 main 브랜치"에서 "이 서버 1대에 `AWS-RunShellScript` 실행"만 가능
- 배포 중 앱이 다시 뜨는 30초~1분 동안 사이트가 응답하지 않는다 (서버 1대 구성)
- 실패하면 Actions 가 빨간불 + 서버 출력이 Actions 로그에 남는다. **자동으로 되돌리지 않는다** (DB 구조가 이미 바뀌었을 수 있음)
- SSM Run Command · OIDC 는 추가 요금 없음

### 처음 1회 설정 (AWS 콘솔, 서울 리전)

**① 서버가 SSM 을 쓸 수 있게 (EC2 역할)**

1. IAM → 역할 → 역할 생성 → 신뢰할 엔터티 `AWS 서비스` · 사용 사례 `EC2`
2. 권한 정책 `AmazonSSMManagedInstanceCore` 선택 → 이름 `suwon-ec2-ssm` → 생성
3. EC2 → 인스턴스 `suwon-app` 선택 → 작업 → 보안 → IAM 역할 수정 → `suwon-ec2-ssm` → 저장
4. 서버에서 에이전트 확인 (Ubuntu 24.04 는 기본 설치): `sudo snap services amazon-ssm-agent` 가 `active`. 몇 분 뒤 Systems Manager → 플릿 관리자에 인스턴스가 `온라인` 으로 보이면 완료 (안 보이면 `sudo snap restart amazon-ssm-agent`)

**② GitHub 을 AWS 에 신분증 발급처로 등록 (OIDC 공급자)**

IAM → 자격 증명 공급자 → 공급자 추가 → `OpenID Connect`

| 칸 | 값 |
|---|---|
| 공급자 URL | `https://token.actions.githubusercontent.com` |
| 대상 | `sts.amazonaws.com` |

**③ GitHub Actions 가 받을 역할**

1. IAM → 역할 → 역할 생성 → `웹 자격 증명` → 공급자 `token.actions.githubusercontent.com` · 대상 `sts.amazonaws.com`
2. GitHub 조직 `gwhong-dev` · 리포지토리 `SuwonCultureMap` · 브랜치 `main` → 다음 (권한 정책은 고르지 않고 넘어감) → 이름 `suwon-github-deploy` → 생성
3. 만든 역할 → 신뢰 관계 탭에서 조건이 아래와 같은지 확인. 콘솔이 `sub` 를 `StringLike` 아래에 만들었어도 값이 같으면 그대로 둔다

```json
"StringEquals": {
  "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
  "token.actions.githubusercontent.com:sub": "repo:gwhong-dev/SuwonCultureMap:ref:refs/heads/main"
}
```

4. 권한 탭 → 권한 추가 → 인라인 정책 생성 → JSON 에 아래를 넣고 `계정ID` · `인스턴스ID` 를 바꾼다 → 이름 `suwon-deploy-ssm`

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "RunDeployOnOneServer",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:계정ID:instance/인스턴스ID",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "ReadDeployResult",
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

5. 역할 요약의 **ARN** (`arn:aws:iam::계정ID:role/suwon-github-deploy`) 을 복사

**④ GitHub 저장소 변수**

GitHub 저장소 → Settings → Secrets and variables → Actions → **Variables** 탭 → New repository variable

| 이름 | 값 |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | ③-5 의 ARN |
| `EC2_INSTANCE_ID` | `i-` 로 시작하는 인스턴스 ID |
| `DEPLOY_DIR` | (서버 저장소 폴더가 `/home/ubuntu/SuwonCultureMap` 가 아닐 때만) |

비밀값이 아니므로 Secrets 가 아니라 Variables 에 넣는다. 둘 중 하나라도 없으면 `Deploy` 는 건너뛴다 (실패 아님).

**⑤ 첫 실행 확인**

GitHub → Actions → `Deploy` → Run workflow (main) → 초록불 + 로그에 `[완료] … 배포 · 응답 확인` · `https://suwonbible.com 응답 확인`.

| 증상 | 원인 · 해결 |
|---|---|
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | ③-3 신뢰 관계의 `sub` 오타, 또는 main 이 아닌 브랜치에서 실행 |
| `InvalidInstanceId` | ① 이 안 됨 (플릿 관리자에 `온라인` 이 아님), 또는 `EC2_INSTANCE_ID` 오타 |
| `AccessDenied ... ssm:SendCommand` | ③-4 정책의 계정 ID · 인스턴스 ID 확인 |
| `서버에서 고친 파일이 있음` | 서버에서 `git status` → 직접 고친 파일을 되돌리거나 PR 로 옮김 |
| `새 앱이 5분 안에 응답하지 않음` | 출력된 앱 로그 확인. DB 구조 변경이 없으면 출력된 되돌리기 명령(`git reset --hard 이전커밋`) 실행 → 서버는 main 에 남아 다음 배포가 이어짐. `git checkout 커밋` 은 쓰지 않는다 (main 에서 벗어나 이후 배포가 `main 브랜치가 아님` 으로 멈춤) |
| `빌드 · 실행 오류` | 출력된 마지막 40줄 확인. 전체는 서버 `.git/suwon-deploy-build.log` |
| `25분 초과` 경고 | Systems Manager → Run Command 에서 명령 ID 로 실제 결과 확인 |

**백업 확인과 복사** (주 1회)

서버 디스크가 고장 나면 DB 와 백업이 함께 사라지므로 내 PC 로 복사한다. PC 에는 최신 1개만 남기고 이전 파일은 지운다.

```bash
tail -n 3 ~/backups/backup.log   # 서버에서
```

```bash
scp -i 키파일.pem ubuntu@서버IP:~/backups/백업파일.sql.gz .
```

**백업 복원** (현재 DB 를 지우고 되돌림)

앱을 켠 채 복원하면 Flyway 기록이 어긋나 다음 시작이 실패할 수 있으므로 이 순서를 지킨다.

```bash
docker compose stop app
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE $MYSQL_DATABASE"'
gzip -dc ~/backups/백업파일.sql.gz | docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
docker compose start app
```

**AWS Free 플랜 종료:** 2027-05-02 또는 크레딧 소진 중 먼저 오는 때. 그 뒤 계정이 닫혀 서비스와 DB 접근이 끊긴다. 잔액을 매달 EC2 대시보드에서 확인하고, **2027-03-01** 까지 Oracle 이전 또는 Paid 전환을 결정한다. 종료 전에 백업을 내 PC 로 받아 둔다.

**하면 안 되는 명령:** `docker compose down -v` · `docker system prune --volumes` (DB 볼륨이 지워진다)
