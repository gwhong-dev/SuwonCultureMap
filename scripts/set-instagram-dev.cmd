@echo off
chcp 65001 >nul
rem set-instagram-dev.cmd - application-dev.properties 에 인스타 토큰 · 내 계정 ID 저장 (토큰은 화면에 안 보임)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0set-instagram-dev.ps1"
pause
