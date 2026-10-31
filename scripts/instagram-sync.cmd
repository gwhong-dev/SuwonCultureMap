@echo off
chcp 65001 >nul
rem instagram-sync.cmd - 실행 중인 앱에 인스타 동기화 1번 요청 → 결과 숫자 출력
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instagram-sync.ps1"
pause
