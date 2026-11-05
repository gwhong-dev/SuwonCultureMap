@echo off
chcp 65001 >nul
rem jenkins-start.cmd - 내 PC Docker 에 Jenkins 를 띄움 (더블클릭) -> http://localhost:8081
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0jenkins-start.ps1"
pause
