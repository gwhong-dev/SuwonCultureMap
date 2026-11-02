#!/usr/bin/env bash
# 운영 서버 자동 배포: main 최신 코드로 앱을 다시 빌드 · 실행하고 응답까지 확인
# 실행: GitHub Actions(deploy.yml) → AWS SSM 이 서버에서 ubuntu 사용자로 실행. 직접 실행해도 됨:
#   bash deploy/deploy.sh [저장소 폴더]   (기본: 이 파일이 있는 저장소)
# 실패하면 0 이 아닌 값으로 끝남 → Actions 가 빨간불. 자동 되돌리기는 하지 않음 (Flyway 로 DB 구조가 바뀌었을 수 있음)
set -euo pipefail

# 전체를 함수로 감싸 끝까지 읽은 뒤 실행 (git pull 로 이 파일이 바뀌어도 실행 중인 내용이 섞이지 않게)
main() {
  local repo_dir="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
  cd "$repo_dir"

  # 수동 배포 · 다른 자동 배포와 겹치지 않게 잠금
  exec 9>/tmp/suwon-deploy.lock
  if ! flock -w 600 9; then
    echo "[오류] 다른 배포가 10분 넘게 진행 중" >&2
    return 1
  fi

  if [ ! -f .env ]; then
    echo "[오류] $repo_dir/.env 가 없음 (docs/deploy-ec2.md 5번)" >&2
    return 1
  fi
  # 서버에서 직접 고친 파일이 있으면 덮어쓰지 않고 멈춤
  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "[오류] 서버에서 고친 파일이 있음 → git status 로 확인 후 정리" >&2
    git status --short --untracked-files=no >&2
    return 1
  fi
  if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
    echo "[오류] main 브랜치가 아님: $(git rev-parse --abbrev-ref HEAD)" >&2
    return 1
  fi

  local before after
  before="$(git rev-parse HEAD)"
  git fetch -q origin main
  git merge -q --ff-only origin/main
  after="$(git rev-parse HEAD)"
  echo "[배포] ${before:0:7} → ${after:0:7} ($(git log -1 --format=%s))"

  # COMPOSE_FILE · COMPOSE_PROJECT_NAME 은 .env 에 있음 → 운영 설정(Caddy 포함)으로 실행
  docker compose up -d --build --remove-orphans

  if ! wait_app; then
    echo "[실패] 새 앱이 5분 안에 응답하지 않음. 최근 로그:" >&2
    docker compose logs --no-color --tail 80 app >&2 || true
    echo "되돌리기(DB 구조 변경이 없을 때만): git checkout ${before:0:7} && docker compose up -d --build" >&2
    return 1
  fi

  # 옛 이미지 정리 (디스크 20GB). 빌드 캐시는 3일 넘은 것만 지워 다음 빌드를 빠르게
  docker image prune -f >/dev/null
  docker builder prune -f --filter until=72h >/dev/null
  echo "[완료] ${after:0:7} 배포 · 응답 확인 ($(date '+%F %T'))"
}

# 앱은 서버 안(127.0.0.1:8080)에서만 열려 있음
wait_app() {
  local i
  for i in $(seq 1 60); do
    if curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/api/events; then
      return 0
    fi
    sleep 5
  done
  return 1
}

main "$@"
exit $?
