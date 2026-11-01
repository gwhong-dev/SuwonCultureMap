#!/usr/bin/env bash
# DB 전체를 압축해 서버의 ~/backups 에 저장하고 14일이 지난 파일은 삭제 (처리방침의 백업 항목과 맞출 것)
# 서버 디스크에만 있으므로 주기적으로 내 PC 로 복사할 것 (docs/deploy-ec2.md)
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS=13   # find -mtime +13 은 만 14일 이상을 고름
FILE="$BACKUP_DIR/db-$(date +%Y%m%d-%H%M%S).sql.gz"
TMP="$FILE.tmp"

mkdir -p "$BACKUP_DIR"
umask 077
trap 'rm -f "$TMP"' EXIT

cd "$REPO_DIR"
# 비밀번호는 컨테이너 안의 환경 변수를 사용 (명령줄 · 로그에 남기지 않음)
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces --databases "$MYSQL_DATABASE"' \
  | gzip > "$TMP"

# 끝까지 정상으로 받은 파일만 남김 (mysqldump 는 마지막 줄에 완료 표시를 남김)
if ! gzip -dc "$TMP" | tail -n 1 | grep -q 'Dump completed'; then
  echo "[오류] $(date '+%F %T') 백업이 끝까지 완료되지 않음" >&2
  exit 1
fi

mv "$TMP" "$FILE"
find "$BACKUP_DIR" -name 'db-*.sql.gz' -type f -mtime +"$KEEP_DAYS" -delete
echo "[완료] $(date '+%F %T') $FILE"
