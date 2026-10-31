@echo off
chcp 65001 >nul
rem set-instagram-accounts.cmd - application-dev.properties 에 인스타 대상 계정 목록 저장 (비밀값 없음)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0set-instagram-accounts.ps1"
pause
