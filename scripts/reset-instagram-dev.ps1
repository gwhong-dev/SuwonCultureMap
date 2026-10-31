# reset-instagram-dev.ps1 — 내 PC DB 의 인스타 행사 · 수집 기록을 한 번 지우고 처음부터 다시 동기화하게 한다
#
# 왜 필요한가 (PR #41): 인스타 행사 키 = 제목 + 시작일 + 장소. 제목 고르기 규칙이 바뀌면 같은 행사도 키가 달라져
#   다시 동기화할 때 새 행사로 저장되고 예전 행사도 남음 → 같은 자리 마커 2개
#
# 하는 일 (검사 통과 + Y 확인 시에만, 한 트랜잭션):
#   1) instagram_block 전부 삭제
#   2) instagram_media 전부 삭제
#   3) event 중 source = 'INSTAGRAM' 인 행만 삭제
#   → 앱에서 인스타 동기화를 다시 실행하면 새 규칙으로 처음부터 저장됨
#
# 건드리지 않는 것: 재단(SWCF) 행사 · 회원 · 로그인 · 찜, 표 구조, Flyway 기록
# 중단 조건: 인스타 행사에 찜이 1개라도 있으면 아무것도 지우지 않음 (사용자 데이터 보호)
# 비밀값: application-dev.properties 의 DB 비밀번호를 임시 설정 파일로만 넘기고, 끝나면 지움. 화면에 출력하지 않음
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

Write-Host ''
Write-Host '===== 수원 문화지도 · 내 PC 인스타 행사 1회 정리 ====='
Write-Host ''

# ---------- 1. 설정 읽기 ----------
$dev = 'src\main\resources\application-dev.properties'
if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}
$props = @{}
foreach ($l in (Get-Content -LiteralPath $dev -Encoding UTF8)) {
    if ($l -match '^\s*([A-Za-z0-9.\-]+)\s*[=:]\s*(.*?)\s*$') { $props[$Matches[1]] = $Matches[2] }
}
$url = $props['spring.datasource.url']
if (-not $url -or $url -notmatch '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') {
    Write-Host '[오류] spring.datasource.url 을 읽지 못함 (jdbc:mysql://호스트:포트/DB이름 모양이어야 함)'
    exit 1
}
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
$dbUser = $props['spring.datasource.username']
$dbPass = $props['spring.datasource.password']
if (-not $dbUser) { Write-Host '[오류] spring.datasource.username 이 비어 있음'; exit 1 }
if ($null -eq $dbPass) { $dbPass = '' }
Write-Host ('[설정] DB = ' + $dbName + ' (' + $dbHost + ':' + $dbPort + '), 사용자 = ' + $dbUser + ', 비밀번호 = (출력 안 함)')

# ---------- 2. mysql.exe 찾기 ----------
$mysql = $null
$cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($cmd) { $mysql = $cmd.Source }
if (-not $mysql) {
    $found = Get-ChildItem -Path "$env:ProgramFiles\MySQL" -Filter mysql.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'MySQL Server' } | Sort-Object FullName -Descending | Select-Object -First 1
    if ($found) { $mysql = $found.FullName }
}
if (-not $mysql) {
    Write-Host '[오류] mysql.exe 를 찾지 못함 (C:\Program Files\MySQL\MySQL Server *\bin 에 있어야 함)'
    exit 1
}
Write-Host ('[도구] ' + $mysql)

# 비밀번호는 명령줄에 넣지 않고 임시 설정 파일로 (작업 끝나면 삭제)
$cnf = Join-Path $env:TEMP ('swcm-reset-ig-' + [guid]::NewGuid().ToString('N') + '.cnf')
$escaped = $dbPass.Replace('\', '\\').Replace('"', '\"')
$escUser = $dbUser.Replace('\', '\\').Replace('"', '\"')
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($cnf, "[client]`r`nuser=`"$escUser`"`r`npassword=`"$escaped`"`r`n", $utf8)

function Sql([string]$q) {
    # Windows PowerShell 5.1 은 Stop 상태에서 mysql 의 경고(stderr) 한 줄에도 멈춤 → 이 호출만 Continue, 성공 여부는 종료 코드로 판단
    $ErrorActionPreference = 'Continue'
    $out = & $mysql "--defaults-extra-file=$cnf" --protocol=TCP -h $dbHost -P $dbPort -D $dbName --default-character-set=utf8mb4 -N -B -e $q 2>&1
    if ($LASTEXITCODE -ne 0) {
        $msg = ($out | ForEach-Object { "$_" }) -join ' '
        throw ('SQL 실패: ' + $msg)
    }
    # 결과에는 표준 출력만 (경고 줄이 값으로 읽히지 않게)
    return @($out | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
}
function One([string]$q) { $r = @(Sql $q); if ($r.Count -gt 0) { return $r[0] } else { return '' } }
function TableExists([string]$t) {
    return (One "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$t'") -eq '1'
}
function ColumnExists([string]$c) {
    return (One "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event' AND COLUMN_NAME = '$c'") -eq '1'
}

try {
    # ---------- 3. 검사 (조회만) ----------
    Write-Host ''
    Write-Host '--- 검사 (조회만, 아직 아무것도 안 바꿈) ---'
    foreach ($t in @('instagram_block', 'instagram_media', 'favorite')) {
        if (-not (TableExists $t)) { Write-Host ("[중단] $t 표가 없음 → 앱을 한 번 실행해 Flyway V2 를 먼저 적용"); exit 1 }
    }
    if (-not (ColumnExists 'source')) { Write-Host '[중단] event.source 칸이 없음 → 앱을 한 번 실행해 Flyway V2 를 먼저 적용'; exit 1 }

    $igEvents = One "SELECT COUNT(*) FROM event WHERE source = 'INSTAGRAM'"
    $swcf = One "SELECT COUNT(*) FROM event WHERE source IS NULL OR source <> 'INSTAGRAM'"
    $blocks = One 'SELECT COUNT(*) FROM instagram_block'
    $medias = One 'SELECT COUNT(*) FROM instagram_media'
    $favs = One "SELECT COUNT(*) FROM favorite f JOIN event e ON e.id = f.event_id WHERE e.source = 'INSTAGRAM'"
    Write-Host ('[현황] 인스타 행사 ' + $igEvents + '건 · 게시물 기록 ' + $medias + '개 · 블록 기록 ' + $blocks + '개')
    Write-Host ('[현황] 재단 행사 ' + $swcf + '건 (그대로 둠)')

    if ($favs -ne '0') {
        Write-Host ("[중단] 인스타 행사에 찜이 $favs 개 있음 → 지우지 않음. 이 화면을 캡처해서 Claude 에게 보내 주세요")
        exit 1
    }
    Write-Host '[OK] 인스타 행사에 찜 없음'
    if ($igEvents -eq '0' -and $blocks -eq '0' -and $medias -eq '0') { Write-Host '[완료] 지울 것이 없음'; exit 0 }

    # ---------- 4. 확인 후 실행 ----------
    Write-Host ''
    Write-Host '--- 실행할 작업 (한 번에, 중간 실패 시 전부 취소) ---'
    Write-Host '  DELETE FROM instagram_block'
    Write-Host '  DELETE FROM instagram_media'
    Write-Host "  DELETE FROM event WHERE source = 'INSTAGRAM'"
    Write-Host ''
    Write-Host '[주의] 다시 동기화하면 계정마다 최근 게시물(기본 10개)만 다시 들어옴 → 더 오래된 글 · 목록에서 뺀 계정의 행사는 돌아오지 않음'
    Write-Host '[주의] 앱(서버)을 끈 상태에서 진행해 주세요 (동기화 중이면 섞일 수 있음)'
    Write-Host '(창을 X 로 닫지 말고 Y 또는 다른 글자 + Enter 로 답해 주세요)'
    $answer = Read-Host '진행할까요? (Y 입력 후 Enter, 그 외는 취소)'
    if ($answer -ne 'Y' -and $answer -ne 'y') { Write-Host '[취소] 아무것도 바꾸지 않음'; exit 0 }

    # 자식(block: media · event 를 가리킴) → media → event 순서. 찜은 위에서 0개 확인 + 같은 트랜잭션 안에서 다시 확인
    Sql ("START TRANSACTION; " +
        "DELETE FROM instagram_block; " +
        "DELETE FROM instagram_media; " +
        "DELETE FROM event WHERE source = 'INSTAGRAM' AND id NOT IN (SELECT event_id FROM favorite WHERE event_id IS NOT NULL); " +
        "COMMIT;") | Out-Null
    Write-Host '[완료] 인스타 블록 · 게시물 기록 · 인스타 행사 삭제'

    # ---------- 5. 결과 확인 ----------
    $leftIg = One "SELECT COUNT(*) FROM event WHERE source = 'INSTAGRAM'"
    $leftSwcf = One "SELECT COUNT(*) FROM event WHERE source IS NULL OR source <> 'INSTAGRAM'"
    Write-Host ''
    Write-Host ('[확인] 인스타 행사 ' + $leftIg + '건 남음 (0이면 정상) · 재단 행사 ' + $leftSwcf + '건 그대로')
    Write-Host '[다음] 앱 실행 → scripts\instagram-sync.cmd 로 다시 동기화 → 지도에서 같은 자리 마커가 1개인지 확인'
}
catch {
    Write-Host ''
    Write-Host ('[오류] ' + $_.Exception.Message)
    Write-Host '이 화면을 캡처해서 Claude 에게 보내 주세요'
    exit 1
}
finally {
    Remove-Item -LiteralPath $cnf -Force -ErrorAction SilentlyContinue
}
