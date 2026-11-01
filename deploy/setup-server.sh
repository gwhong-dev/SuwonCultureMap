#!/usr/bin/env bash
# 새 Ubuntu 서버 준비: Docker 설치, 메모리 보조(스왑) 2GB, 보안 자동 업데이트, 매일 DB 백업 예약
# 사용: sudo bash deploy/setup-server.sh  (저장소 폴더에서 실행, 여러 번 실행해도 안전)
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "[오류] sudo 로 실행하세요: sudo bash deploy/setup-server.sh" >&2
  exit 1
fi

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_USER="${SUDO_USER:-ubuntu}"

apt-get update -y
apt-get install -y docker.io docker-compose-v2 unattended-upgrades
systemctl enable --now docker
usermod -aG docker "$RUN_USER"

# 이미지 빌드 중 메모리가 모자라 멈추지 않도록 스왑 추가
if ! swapon --show | grep -q '/swapfile'; then
  if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
  fi
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

dpkg-reconfigure -f noninteractive unattended-upgrades

# 매일 04:00(한국 시간) DB 백업
timedatectl set-timezone Asia/Seoul
install -d -o "$RUN_USER" -g "$RUN_USER" "/home/$RUN_USER/backups"
cat > /etc/cron.d/suwon-db-backup <<EOF
0 4 * * * $RUN_USER bash $REPO_DIR/deploy/backup-db.sh >> /home/$RUN_USER/backups/backup.log 2>&1
EOF
chmod 644 /etc/cron.d/suwon-db-backup

echo "[완료] 서버 준비 끝. 로그아웃 후 다시 접속하면 sudo 없이 docker 를 쓸 수 있습니다."
