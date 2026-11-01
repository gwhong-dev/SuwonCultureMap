# AWS EC2 배포 절차

구성: 인터넷 → **Caddy**(HTTPS) → **앱**(Spring Boot) → **MySQL**. 셋 다 EC2 서버 1대의 Docker 컨테이너.

- 외부에 여는 포트: 80 · 443(웹), 22(관리 접속, 내 IP 만)
- 앱 8080 · DB 3307 은 서버 안에서만 접속 가능
- 동기화는 앱이 자동 실행 (매일 06시 · 18시, 재단 행사가 없는 새 DB 는 시작 직후)

아래 명령의 `dc` 는 다음을 줄인 것이다. 서버에서 한 번 등록해 둔다.

```bash
echo "alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'" >> ~/.bash_aliases
```

## 1. 서버 만들기 (AWS 콘솔, 서울 리전)

| 항목 | 값 |
|---|---|
| 이름 | `suwon-app` |
| 이미지 | Ubuntu Server 24.04 LTS, 64비트(Arm) |
| 인스턴스 유형 | `t4g.small` (2GB) |
| 크레딧 사양 | **Standard** (Unlimited 는 추가 과금될 수 있음) |
| 키 페어 | 새로 만들어 `.pem` 을 내 PC 에 보관 |
| 저장 공간 | 20GB gp3 |
| 보안 그룹 | SSH(22): 내 IP, HTTP(80) · HTTPS(443): 전체 |

서버를 만든 뒤 **탄력적 IP** 를 하나 받아 연결한다. 재시작해도 주소가 바뀌지 않게 하기 위함.

## 2. 도메인 연결 (Cloudflare → DNS)

| 유형 | 이름 | 값 | 프록시 |
|---|---|---|---|
| A | `@` | 탄력적 IP | DNS 전용(회색 구름) |
| A | `www` | 탄력적 IP | DNS 전용(회색 구름) |

- 프록시를 끄는 이유: HTTPS 인증서는 Caddy 가 직접 발급 · 갱신한다
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
```

- `APP_BASE_URL` 을 localhost 그대로 두면 소셜 로그인이 실패한다
- `KAKAO_ADMIN_KEY` 는 운영에서 필수 (탈퇴 때 카카오 연결 해제)
- 비밀번호에는 특수문자를 쓰지 않는다 (`$` 등을 compose 가 다르게 해석)
- 인스타 수집을 쓰면 `INSTAGRAM_*` 도 넣는다

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
dc up -d --build
dc logs -f app
```

첫 실행 로그에서 `재단 동기화` · `자동 동기화 완료` 를 확인한다. `일부 실패` 가 나오면 원인을 확인한다. 재배포 때는 이 로그가 다음 예약 시각까지 나오지 않는다.

- [ ] `https://suwonbible.com` 접속, `http://` · `www` 는 본 주소로 이동
- [ ] 처리방침에 `배포 전 입력` 문구 없음
- [ ] `POST /api/events/sync` 가 200 이 아님 (운영에는 수동 API 없음)
- [ ] 외부에서 3307 · 8080 접속 불가
- [ ] 지도 · 행사 이미지 표시 (브라우저 콘솔에 혼합 콘텐츠 차단 없음)
- [ ] 로그인 3종 · 찜 · 탈퇴, **테스터로 등록하지 않은 계정**으로도 로그인
- [ ] `bash deploy/backup-db.sh` 후 `~/backups` 에 파일 생성
- [ ] `docker stats --no-stream` · `free -h` · `df -h` 로 메모리 · 디스크 여유 확인. 메모리가 부족하면 DB 에 `--performance-schema=OFF` 를 먼저 시도
- [ ] `dc stop app` 후 사이트 요청 → `dc logs caddy` 에 방문자 IP 가 남는지 확인 → 결과를 처리방침에 반영 → `dc start app`

## 운영

**코드 갱신** (빌드 중 응답이 느려질 수 있음)

```bash
git pull
dc up -d --build
docker image prune -f
docker builder prune -f
```

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
dc stop app
dc exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE $MYSQL_DATABASE"'
gzip -dc ~/backups/백업파일.sql.gz | dc exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
dc start app
```

**AWS Free 플랜 종료:** 2027-05-02. 4월 초에 Oracle 이전 또는 Paid 전환을 결정한다. 종료 전에 백업을 내 PC 로 받아 둔다.
