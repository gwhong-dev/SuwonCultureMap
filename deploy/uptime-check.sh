#!/usr/bin/env bash
# =====================================================
#  운영 가동 감시 — .github/workflows/uptime.yml 이 15분마다 실행
#  1) /actuator/health 가 200 + {"status":"UP"} 인지 확인 (배포 재시작 중일 수 있어 여러 번 재시도)
#  2) 응답 없음 → "uptime" 라벨 Issue 를 1개만 열어 장애 기록 (이미 열려 있으면 새로 안 만듦)
#  3) 다시 정상 → 열린 Issue 에 복구 시각을 남기고 닫음
#  종료 코드: 0 정상 · 복구 · 장애 계속(Issue 이미 있음) / 1 새 장애 / 2 gh 조회 · 닫기 실패(Issue 건드리지 않음)
#  → 장애가 길어도 실패 알림은 처음 1번만 (기록은 Issue 하나에)
#  필요한 것: curl, gh(GH_TOKEN), GITHUB_REPOSITORY. 시험은 가짜 curl · gh 로 (PR 본문 참고)
# =====================================================
set -u # -e 는 안 씀: curl 실패도 재시도 대상이라 직접 처리

url="${HEALTH_URL:-https://suwonbible.com/actuator/health}"
# 11번 × 30초 ≈ 5분: deploy/deploy.sh 가 앱 기동을 최대 5분 기다리므로 배포 중 오탐 방지
tries="${TRIES:-11}"
wait_seconds="${WAIT_SECONDS:-30}"
label="uptime"
run_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
now="$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M KST')"

ok=0
detail=""
for i in $(seq 1 "$tries"); do
  # 마지막 줄에 HTTP 상태 코드를 붙여 받음 (연결 실패면 000)
  out="$(curl -sS --max-time 15 -w $'\n%{http_code}' "$url" 2>&1)"
  code="${out##*$'\n'}"
  body="${out%$'\n'*}"
  if [ "$code" = "200" ] && [[ "$body" == *'"status":"UP"'* ]]; then
    ok=1
    break
  fi
  detail="시도 ${i}/${tries}: HTTP ${code} ${body:0:200}"
  echo "$detail"
  if [ "$i" -lt "$tries" ]; then sleep "$wait_seconds"; fi
done

# 조회가 실패하면 열린 Issue 가 있는지 모름 → 중복 생성 방지를 위해 아무것도 안 하고 끝냄
if ! open_issue="$(gh issue list --label "$label" --state open --json number --jq '.[0].number // empty')"; then
  echo "장애 Issue 조회 실패 — Issue 를 건드리지 않고 종료"
  exit 2
fi

if [ "$ok" = "1" ]; then
  if [ -n "$open_issue" ]; then
    gh issue comment "$open_issue" --body "✅ 복구 확인: ${now} — ${url} 정상 응답 ([실행 기록](${run_url}))"
    if ! gh issue close "$open_issue"; then
      echo "Issue #${open_issue} 닫기 실패"
      exit 2
    fi
    echo "복구됨 → Issue #${open_issue} 닫음"
  else
    echo "정상: ${url}"
  fi
  exit 0
fi

if [ -n "$open_issue" ]; then
  echo "응답 없음 — 이미 열린 장애 Issue #${open_issue} 가 있어 새로 만들지 않음 (알림 반복 방지로 성공 처리)"
  exit 0
else
  gh label create "$label" --color B60205 --description "가동 감시 자동 장애 기록" --force >/dev/null
  gh issue create --label "$label" \
    --title "🚨 운영 응답 없음 (${now})" \
    --body "$(printf '%s\n' \
      "가동 감시가 ${tries}번 확인했지만 정상 응답이 없음." \
      "" \
      "- 주소: ${url}" \
      "- 마지막 결과: ${detail}" \
      "- 실행 기록: ${run_url}" \
      "" \
      "### 기록할 것" \
      "- 원인:" \
      "- 조치:" \
      "- 재발 방지:" \
      "" \
      "다시 정상이 되면 이 Issue 는 가동 감시가 자동으로 닫음.")"
  echo "응답 없음 → 장애 Issue 생성"
fi
exit 1
