@echo off
chcp 65001 >nul
rem reset-instagram-dev.cmd - 내 PC DB 의 인스타 행사 · 수집 기록 1회 정리 (검사 후 Y 확인 시에만, 재단 행사 · 찜은 그대로)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-instagram-dev.ps1"
pause
