@echo off
chcp 65001 >nul
rem instagram-peek.cmd - 인스타 대상 계정의 최근 게시물(캡션 등)을 화면에만 보여줌 (더블클릭)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instagram-peek.ps1" %*
pause
