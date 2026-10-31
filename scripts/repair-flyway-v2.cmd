@echo off
chcp 65001 >nul
rem repair-flyway-v2.cmd - Flyway V2 "Duplicate column name 'source'" 실패 복구 (검사 후 Y 확인 시에만 변경)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0repair-flyway-v2.ps1"
pause
