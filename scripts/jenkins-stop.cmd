@echo off
chcp 65001 >nul
rem jenkins-stop.cmd - Jenkins 끄기 (설정 · 빌드 기록 · Maven 캐시는 볼륨에 남음)
cd /d "%~dp0.."
docker compose -f jenkins\docker-compose.yml --env-file jenkins\.env stop
echo.
echo Jenkins 를 껐습니다. 다시 켜려면 scripts\jenkins-start.cmd
pause
