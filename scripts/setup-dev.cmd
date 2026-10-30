@echo off
chcp 65001 >nul
setlocal

rem ==========================================================
rem  setup-dev.cmd — PR #8(설정 파일 분리) 머지 전 로컬 준비
rem  - 더블클릭으로 실행. 여러 번 실행해도 안전함
rem  - 비밀값은 화면에 출력하지 않고, 외부로 보내지도 않음
rem ==========================================================

rem ── 자기 자신을 임시 폴더로 복사해서 실행 ──
rem   git pull 이 이 파일 자체를 바꾸면 실행 중인 cmd가 꼬이므로,
rem   복사본으로 실행하고 프로젝트 루트(scripts 의 상위 폴더)를 인자로 넘김
if /i not "%~1"=="--run" (
    copy /y "%~f0" "%TEMP%\suwon-setup-dev.cmd" >nul || (
        echo [오류: 임시 폴더에 스크립트를 복사하지 못함]
        pause
        exit /b 1
    )
    call "%TEMP%\suwon-setup-dev.cmd" --run "%~dp0.."
    exit /b
)

rem ── 프로젝트 루트로 이동 (드라이브 문자 하드코딩 없음) ──
cd /d "%~2" || (
    echo [오류: 프로젝트 폴더로 이동하지 못함]
    goto end
)

set "RES=src\main\resources"
set "PROPS=%RES%\application.properties"
set "DEV=%RES%\application-dev.properties"
set "EXCLUDE=.git\info\exclude"
set "PULL_LOG=%TEMP%\suwon-setup-dev-pull.log"
set "NO_DEV="

echo.
echo ===== 수원 문화지도 · 개발 환경 준비 =====
echo.

rem ==========================================================
rem  0. git pull  (실패하면 아래 단계는 진행하지 않음)
rem ==========================================================
echo [0] git pull
git pull >"%PULL_LOG%" 2>&1
if not errorlevel 1 goto pull_ok

findstr /c:"dubious ownership" "%PULL_LOG%" >nul
if errorlevel 1 goto pull_fail

echo     소유권 경고 감지 → safe.directory 등록 후 다시 시도
git config --global --add safe.directory "*"
git pull >"%PULL_LOG%" 2>&1
if errorlevel 1 goto pull_fail

:pull_ok
type "%PULL_LOG%"
echo     [완료]
goto step1

:pull_fail
type "%PULL_LOG%"
echo     [오류: git pull 실패 → 아래 단계를 진행하지 않고 멈춤]
findstr /c:"would be overwritten" "%PULL_LOG%" >nul
if not errorlevel 1 echo     힌트: PR #8 이 이미 머지된 상태라면 application.properties 를 application-dev.properties 로 직접 이름 바꾼 뒤 다시 실행
goto end

rem ==========================================================
rem  1. 프로젝트 루트 확인 (상대경로 기준점)
rem ==========================================================
:step1
echo.
echo [1] 프로젝트 루트 확인
if not exist "pom.xml" (
    echo     [오류: pom.xml 이 없음 → 프로젝트 루트가 아님. 멈춤]
    goto end
)
if not exist "%RES%\" (
    echo     [오류: src\main\resources 폴더가 없음. 멈춤]
    goto end
)
echo     [완료] "%CD%"

rem ==========================================================
rem  2. application.properties → application-dev.properties 이름 변경
rem ==========================================================
echo.
echo [2] 비밀값 파일 이름 변경
if exist "%PROPS%" if not exist "%DEV%" goto rename_do
if exist "%PROPS%" if exist "%DEV%" goto rename_both
if exist "%DEV%" goto rename_already

echo     [오류: application.properties 도 application-dev.properties 도 없음 → USB에서 비밀값 파일을 먼저 복사]
set "NO_DEV=1"
goto step3

:rename_do
move "%PROPS%" "%DEV%" >nul
if errorlevel 1 (
    echo     [오류: 이름 변경 실패 → IDE 등에서 파일을 열고 있는지 확인]
    set "NO_DEV=1"
    goto step3
)
echo     [완료] application.properties → application-dev.properties
goto step3

:rename_both
rem PR #8 머지 후에는 application.properties 가 Git이 관리하는 "공통 설정" 파일 → 정상 상태
git ls-files --error-unmatch "%PROPS%" >nul 2>&1
if not errorlevel 1 (
    echo     [건너뜀: 이미 이름 변경됨. application.properties 는 Git이 관리하는 공통 설정 파일]
    goto step3
)
echo     [건너뜀: 경고 - 두 파일이 모두 있음. 어느 쪽이 실제 값인지 직접 확인 필요. 아무것도 바꾸지 않음]
goto step3

:rename_already
echo     [건너뜀: 이미 application-dev.properties 로 이름 변경됨]
goto step3

rem ==========================================================
rem  3. app.base-url 줄이 없으면 맨 아래에 추가
rem ==========================================================
:step3
echo.
echo [3] app.base-url 추가
if defined NO_DEV (
    echo     [건너뜀: application-dev.properties 가 없음]
    goto step4
)
findstr /r /c:"^ *app\.base-url *[=:]" "%DEV%" >nul
if not errorlevel 1 (
    echo     [건너뜀: 이미 app.base-url 줄이 있음]
    goto step4
)
rem 마지막 줄에 줄바꿈이 없을 수 있으므로 빈 줄을 먼저 넣고 추가
(echo.& echo app.base-url=http://localhost:8080)>>"%DEV%" || (
    echo     [오류: 파일에 쓰지 못함]
    goto step4
)
echo     [완료] app.base-url=http://localhost:8080 추가

rem ==========================================================
rem  4. .git\info\exclude 에 application-dev.properties 추가
rem     (지금 main 의 .gitignore 는 application.properties 만 제외하므로
rem      이름 변경 후 실수로 커밋되지 않게 "내 PC에서만" 보호)
rem ==========================================================
:step4
echo.
echo [4] Git 커밋 방지 설정 (.git\info\exclude)
findstr /x /c:"application-dev.properties" "%EXCLUDE%" >nul 2>&1
if not errorlevel 1 (
    echo     [건너뜀: 이미 exclude 에 등록됨]
    goto step5
)
if not exist ".git\info\" mkdir ".git\info"
(echo.& echo # local secrets - added by scripts/setup-dev.cmd& echo application-dev.properties)>>"%EXCLUDE%" || (
    echo     [오류: exclude 파일에 쓰지 못함]
    goto step5
)
git check-ignore -q "%DEV%"
if errorlevel 1 (
    echo     [오류: 추가는 했지만 Git이 무시하지 않음 → 직접 확인 필요]
    goto step5
)
echo     [완료] application-dev.properties 는 이제 git add 해도 올라가지 않음

rem ==========================================================
rem  5. application-dev.properties 의 "키 이름만" 출력 (값은 절대 출력 안 함)
rem ==========================================================
:step5
echo.
echo [5] application-dev.properties 키 목록 - 값은 출력하지 않음
if defined NO_DEV (
    echo     [건너뜀: application-dev.properties 가 없음]
    goto end
)
rem PowerShell 로 각 줄에서 "= 또는 : 앞의 키 이름"만 뽑아서 출력
rem   - # / ! 로 시작하는 주석, 빈 줄은 건너뜀
rem   - 직전 줄이 \ 로 끝나면 값이 이어지는 줄이므로 출력하지 않음
rem   - 실패하면 아무것도 출력하지 않음 (값 노출 방지)
set "SETUP_DEV_FILE=%DEV%"
powershell -NoProfile -Command "$cont=$false; foreach($l in Get-Content -LiteralPath $env:SETUP_DEV_FILE){ if(-not $cont -and $l -match '^\s*([^#!\s=:][^\s=:]*)'){ '    - ' + $Matches[1] }; $cont=$l.EndsWith('\') }"
if errorlevel 1 (
    echo     [오류: 키 목록을 읽지 못함 - 값 보호를 위해 아무것도 출력하지 않음]
    goto end
)
echo     [완료]

:end
if exist "%PULL_LOG%" del "%PULL_LOG%" >nul 2>&1
echo.
echo ===== 끝 =====
pause
endlocal
