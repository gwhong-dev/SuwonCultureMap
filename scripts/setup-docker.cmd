@echo off
chcp 65001 >nul
rem setup-docker.cmd - docker compose 용 .env 를 application-dev.properties 에서 자동 생성 (더블클릭)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-docker.ps1"
pause
