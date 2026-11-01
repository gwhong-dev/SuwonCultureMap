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
| 키 페어 | 새로 만들어 `.pem` 을 내 PC 에 보관 |
| 저장 공간 | 20GB gp3 |
| 보안 그룹 | SSH(22): 내 IP, HTTP(80) · HTTPS(443): 전체 |

서버를 만든 뒤 **탄력적 IP** 를 하나 받아 연결한다. 재시작해도 주소가 바뀌지 않게 하기 위함.

## 2. 도메인 연결 (Cloudflare → DNS)

| 유형 | 이름 | 값 | 프록시 |
|---|---|---|---|
| A | `@` | 탄력적 IP | DNS 전용(회색 구름) |
| A | `www` | 탄력적 IP | DNS 전용(회색 구름) |

프록시를 끄는 이유: HTTPS 인증서는 Caddy 가 직접 발급 · 갱신한다.

## 3. 서버 준비

```bash
git clone https://github.com/gwhong-dev/SuwonCultureMap.git
cd SuwonCultureMap
sudo bash deploy/setup-server.sh
exit
```

다시 접속한다 (docker 권한 반영).

## 4. 비밀값 파일 `.env`

서버의 저장소 폴더에 `.env` 를 만든다. 항목은 `.env.example` 참고. Git 에 올리지 않는다.

```
SITE_DOMAIN=suwonbible.com
APP_BASE_URL=https://suwonbible.com
SESSION_COOKIE_SECURE=true
```

- `DB_PASSWORD` · `MYSQL_ROOT_PASSWORD` 는 서버용으로 새로 만든다
- `INSTAGRAM_ACCESS_TOKEN` · `INSTAGRAM_USER_ID` · `INSTAGRAM_TOKEN_EXPIRES_AT` · `INSTAGRAM_ACCOUNTS` 도 함께 넣는다
- 권한 제한: `chmod 600 .env`

## 5. 실행

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f app
```

로그에서 `재단 동기화` · `자동 동기화 완료` 를 확인한다. `일부 실패` 가 나오면 원인을 확인한다.

## 6. 소셜 로그인 · 처리방침

- 카카오 · 구글 · 네이버 콘솔에 콜백 주소 등록: `https://suwonbible.com/api/auth/{kakao|google|naver}/callback`
- 카카오맵 JS 키의 웹 도메인에 `https://suwonbible.com` 등록
- `privacy.html` 의 남은 입력 칸을 실제 구성대로 채운다

## 7. 공개 전 확인

- [ ] `https://suwonbible.com` 접속, `http://` · `www` 는 본 주소로 이동
- [ ] `POST /api/events/sync` 가 200 이 아님 (운영에는 수동 API 없음)
- [ ] 외부에서 3307 · 8080 접속 불가
- [ ] 지도 · 로그인 3종 · 찜 · 탈퇴
- [ ] `bash deploy/backup-db.sh` 후 `~/backups` 에 파일 생성

## 운영

**코드 갱신**

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

**백업 복원** (기존 데이터를 덮어쓰므로 주의)

```bash
gzip -dc ~/backups/백업파일.sql.gz | docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
```

**내 PC 로 백업 받기** (서버를 지우거나 옮기기 전 필수)

```bash
scp -i 키파일.pem ubuntu@서버IP:~/backups/백업파일.sql.gz .
```

**AWS Free 플랜 종료:** 2027-05-02. 4월 초에 Oracle 이전 또는 Paid 전환을 결정한다. 종료 전에 백업을 내 PC 로 받아 둔다.
