@echo off
chcp 65001 >nul
rem jenkins-stop.cmd - Jenkins 끄기 (설정 · 빌드 기록 · Maven 캐시는 볼륨에 남음)
cd /d "%~dp0.."
if not exist jenkins\.env (
  echo [건너뜀] jenkins\.env 가 없음 - Jenkins 를 켠 적이 없음 ^(켜기: scripts\jenkins-start.cmd^)
  pause
  exit /b 0
)
docker compose -f jenkins\docker-compose.yml --env-file jenkins\.env stop
echo.
echo Jenkins 를 껐습니다. 다시 켜려면 scripts\jenkins-start.cmd
pause
