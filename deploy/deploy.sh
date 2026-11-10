#!/usr/bin/env bash
# 운영 서버 자동 배포: main 최신 코드로 앱을 다시 빌드 · 실행하고 응답까지 확인
# 실행: GitHub Actions(deploy.yml) → AWS SSM 이 서버에서 ubuntu 사용자로 실행. 직접 실행해도 됨:
#   bash deploy/deploy.sh [저장소 폴더] [커밋]   (기본: 이 파일이 있는 저장소, origin/main 최신)
#   커밋을 주면 그 커밋까지만 배포 (Actions 는 CI 가 통과한 커밋을 넘김)
# 실패하면 0 이 아닌 값으로 끝남 → Actions 가 빨간불. 자동 되돌리기는 하지 않음 (Flyway 로 DB 구조가 바뀌었을 수 있음)
set -euo pipefail

# 전체를 함수로 감싸 끝까지 읽은 뒤 실행 (git pull 로 이 파일이 바뀌어도 실행 중인 내용이 섞이지 않게)
main() {
  local repo_dir="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
  local target="${2:-}"
  cd "$repo_dir"
  # 잠금 · 빌드 기록은 .git 안에 둠 (Git 이 추적하지 않고, /tmp 처럼 다른 사용자 파일과 부딪히지 않음)
  local work_dir="$repo_dir/.git"
  local build_log="$work_dir/suwon-deploy-build.log"

  # 수동 배포 · 다른 자동 배포와 겹치지 않게 잠금
  exec 9>"$work_dir/suwon-deploy.lock"
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
  if [ -z "$target" ]; then
    target="$(git rev-parse origin/main)"
  fi
  # main 에 실제로 들어간 커밋만 (다른 브랜치 · 엉뚱한 값 차단)
  if ! git merge-base --is-ancestor "$target" origin/main; then
    echo "[오류] $target 은 origin/main 에 없는 커밋" >&2
    return 1
  fi
  # 더 새 버전이 이미 배포됐으면 (배포가 줄 서 있다 늦게 실행된 경우) 뒤로 가지 않음
  if [ "$(git rev-parse "$target")" != "$before" ] && git merge-base --is-ancestor "$target" "$before"; then
    echo "[건너뜀] 서버가 이미 더 새 버전 ${before:0:7} (요청 ${target:0:7})"
    return 0
  fi
  git merge -q --ff-only "$target"
  after="$(git rev-parse HEAD)"
  echo "[배포] ${before:0:7} → ${after:0:7} ($(git log -1 --format=%s))"

  # COMPOSE_FILE · COMPOSE_PROJECT_NAME 은 .env 에 있음 → 운영 설정(Caddy 포함)으로 실행
  # 빌드 진행 출력은 길어서 파일로 (SSM 이 돌려주는 출력은 앞부분만이라 정작 오류가 잘림)
  if ! docker compose up -d --build --remove-orphans >"$build_log" 2>&1; then
    echo "[실패] 빌드 · 실행 오류. 마지막 40줄 (전체: $build_log):" >&2
    tail -n 40 "$build_log" | mask_log >&2
    return 1
  fi

  if ! wait_app; then
    echo "[실패] 새 앱이 5분 안에 응답하지 않음. 최근 로그 40줄 (IP · 메일 · 토큰 가림, 전체: 서버에서 docker compose logs app):" >&2
    docker compose logs --no-color --tail 40 app 2>&1 | mask_log >&2 || true
    echo "되돌리기(DB 구조 변경이 없을 때만): git reset --hard ${before:0:7} && docker compose up -d --build" >&2
    echo "  → 서버는 main 에 남고, 고친 코드가 main 에 머지되면 다음 배포가 이어서 진행됨" >&2
    return 1
  fi

  # Caddyfile 은 파일 하나만 연결(bind mount)돼 git 이 파일을 바꿔 쓰면 실행 중인 Caddy 는 옛 내용을 계속 봄
  # → 컨테이너 안 내용과 저장소 파일이 다를 때만 Caddy 를 새로 만듦 (같으면 건드리지 않아 HTTPS 끊김 없음)
  if docker compose config --services 2>/dev/null | grep -qx caddy && ! caddy_config_same; then
    echo "[Caddy] Caddyfile 바뀜 → Caddy 다시 띄움"
    if ! docker compose up -d --force-recreate --no-deps caddy >>"$build_log" 2>&1 || ! caddy_config_same; then
      echo "[실패] Caddy 다시 띄우기 오류. 최근 로그:" >&2
      docker compose logs --no-color --tail 40 caddy 2>&1 | mask_log >&2 || true
      return 1
    fi
  fi

  # 옛 이미지 정리 (디스크 20GB). 빌드 캐시는 3일 넘은 것만 지워 다음 빌드를 빠르게
  docker image prune -f >/dev/null
  docker builder prune -f --filter until=72h >/dev/null
  echo "[완료] ${after:0:7} 배포 · 응답 확인 ($(date '+%F %T'))"
}

# 앱은 서버 안(127.0.0.1:8080)에서만 열려 있음. 헬스체크는 DB 연결까지 확인 (DOWN 이면 503)
wait_app() {
  local i
  for i in $(seq 1 60); do
    if curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/actuator/health; then
      return 0
    fi
    sleep 5
  done
  return 1
}

# 실패 진단 출력은 SSM → 공개 저장소 Actions 로그(90일)에 남음 → 방문자 IP · 메일 · 토큰 · 비밀번호 값을 가림
# 원본 로그는 서버에만 (docker compose logs · app-logs 볼륨). 시각(12:34:56)은 IP 로 보지 않음
mask_log() {
  sed -E \
    -e 's/\b[0-9]{1,3}(\.[0-9]{1,3}){3}\b/x.x.x.x/g' \
    -e 's/\b([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b/[ipv6]/g' \
    -e 's/\b[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4})*::([0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4})*\b)?|::[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4})*\b/[ipv6]/g' \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/***@***/g' \
    -e 's/\b(Bearer|Basic) [A-Za-z0-9._~+\/=-]+/\1 ***/Ig' \
    -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9._-]+/***/g' \
    -e 's/([?&](code|state|access_token|id_token)=)[^&" ]+/\1***/g' \
    -e "s/(^|[^A-Za-z0-9_])(password|passwd|secret|token|api[_-]?key|authorization|cookie|jsessionid)([\"']?[[:space:]]*[:=][[:space:]]*\[?[\"']?)[^\"' ,&;]+/\1\2\3***/Ig"
}

# 실행 중인 Caddy 가 보는 설정 = 저장소의 deploy/Caddyfile 인지 (Caddy 가 안 떠 있으면 다름으로 봄)
caddy_config_same() {
  local running
  running="$(docker compose exec -T caddy cat /etc/caddy/Caddyfile 2>/dev/null)" || return 1
  [ "$running" = "$(cat deploy/Caddyfile)" ]
}

main "$@"
exit $?
