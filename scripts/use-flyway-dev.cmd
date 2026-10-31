@echo off
chcp 65001 >nul
rem use-flyway-dev.cmd - application-dev.properties 의 ddl-auto 를 validate 로 바꿈 (Flyway 도입)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0use-flyway-dev.ps1"
pause
