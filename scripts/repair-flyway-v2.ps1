# repair-flyway-v2.ps1 — Flyway V2 가 "Duplicate column name 'source'" 로 실패한 내 PC DB 를 V2 실행 전 상태로 되돌린다
#
# 왜 생기나: Flyway 도입 전 ddl-auto=update 로 B안 코드가 한 번 실행되면,
#   Hibernate 가 event.source · source_key, 출처 유니크 인덱스, instagram_media · instagram_block 표를 먼저 만들어 둠
#   → V2 가 "이미 있음" 으로 실패. 이때 source 값은 비어 있음 (SWCF 기본값이 안 들어감)
#
# 하는 일 (아래 검사를 모두 통과할 때만, 실행 전에 Y 확인):
#   1) instagram_block · instagram_media 표 삭제        (두 표 모두 0행일 때만)
#   2) event 의 source · source_key 가 들어간 인덱스 삭제 (이름은 PC 마다 달라서 DB 에서 찾음)
#   3) event.source_key · event.source 칸 삭제           (source_key 가 모두 비어 있고, source 가 비었거나 SWCF 일 때만)
#   4) flyway_schema_history 의 실패한 V2 기록(success=0) 1줄 삭제
#   → 앱을 다시 실행하면 V2 가 처음부터 정식으로 적용됨
#
# 건드리지 않는 것: 재단 행사 행 · 회원 · 로그인 · 찜, event.idx (V2 첫 줄이 다시 같은 값으로 맞춤)
# 비밀값: application-dev.properties 의 DB 비밀번호를 읽어 임시 설정 파일로만 넘기고, 끝나면 지움. 화면에 출력하지 않음
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

Write-Host ''
Write-Host '===== 수원 문화지도 · Flyway V2 실패 복구 ====='
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
$cnf = Join-Path $env:TEMP ('swcm-repair-' + [guid]::NewGuid().ToString('N') + '.cnf')
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
    $stop = $false

    if (-not (TableExists 'flyway_schema_history')) {
        Write-Host '[중단] flyway_schema_history 표가 없음 → 복구할 실패 기록이 없음. 앱을 그냥 실행해 보세요'
        exit 1
    }
    $failed = One "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 0"
    $ok = One "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 1"
    if ($ok -ne '0') { Write-Host '[중단] V2 가 이미 성공으로 기록됨 → 복구할 것 없음'; exit 0 }
    if ($failed -ne '1') { Write-Host ('[중단] 실패한 V2 기록이 1줄이 아님 (' + $failed + '줄) → 수동 확인 필요'); exit 1 }
    Write-Host '[OK] 실패한 V2 기록 1줄'

    $tables = @('instagram_block', 'instagram_media') | Where-Object { TableExists $_ }
    foreach ($t in $tables) {
        $n = One "SELECT COUNT(*) FROM $t"
        if ($n -ne '0') { Write-Host ("[중단] $t 에 데이터 $n 행 → 지우지 않음"); $stop = $true }
        else { Write-Host ("[OK] $t 0행 → 삭제 대상") }
    }

    $hasKey = ColumnExists 'source_key'
    $hasSource = ColumnExists 'source'
    if ($hasKey) {
        $n = One 'SELECT COUNT(*) FROM event WHERE source_key IS NOT NULL'
        if ($n -ne '0') { Write-Host ("[중단] event.source_key 값이 있는 행 $n 개 → 지우지 않음"); $stop = $true }
        else { Write-Host '[OK] event.source_key 모두 비어 있음 → 삭제 대상' }
    }
    if ($hasSource) {
        $n = One "SELECT COUNT(*) FROM event WHERE source IS NOT NULL AND source NOT IN ('', 'SWCF')"
        if ($n -ne '0') { Write-Host ("[중단] event.source 에 SWCF 가 아닌 값 $n 행 → 지우지 않음"); $stop = $true }
        else { Write-Host '[OK] event.source 는 비었거나 SWCF 뿐 → 삭제 대상 (V2 가 SWCF 로 다시 채움)' }
    }

    # source · source_key 가 들어간 event 인덱스 (PRIMARY 제외, 이름은 PC 마다 다름)
    $indexes = @(Sql ("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() " +
        "AND TABLE_NAME = 'event' AND COLUMN_NAME IN ('source', 'source_key') AND INDEX_NAME <> 'PRIMARY'") |
        Where-Object { $_ -ne '' })
    foreach ($i in $indexes) {
        if ($i -notmatch '^[A-Za-z0-9_]+$') { Write-Host ("[중단] 인덱스 이름이 예상과 다름: $i"); $stop = $true }
        else { Write-Host ("[OK] 인덱스 $i → 삭제 대상") }
    }

    if ($stop) {
        Write-Host ''
        Write-Host '[중단] 위 [중단] 항목 때문에 아무것도 바꾸지 않았습니다. 이 화면을 캡처해서 Claude 에게 보내 주세요'
        exit 1
    }

    # ---------- 4. 확인 후 실행 ----------
    Write-Host ''
    Write-Host '--- 실행할 작업 ---'
    foreach ($t in $tables) { Write-Host ("  DROP TABLE $t") }
    foreach ($i in $indexes) { Write-Host ("  ALTER TABLE event DROP INDEX $i") }
    if ($hasKey) { Write-Host '  ALTER TABLE event DROP COLUMN source_key' }
    if ($hasSource) { Write-Host '  ALTER TABLE event DROP COLUMN source' }
    Write-Host "  DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0"
    Write-Host ''
    Write-Host '(창을 X 로 닫지 말고 Y 또는 다른 글자 + Enter 로 답해 주세요)'
    $answer = Read-Host '진행할까요? (Y 입력 후 Enter, 그 외는 취소)'
    if ($answer -ne 'Y' -and $answer -ne 'y') { Write-Host '[취소] 아무것도 바꾸지 않음'; exit 0 }

    # 표는 자식(block) → 부모(media) 순서, 인덱스는 칸보다 먼저
    foreach ($t in @('instagram_block', 'instagram_media')) { if ($tables -contains $t) { Sql "DROP TABLE $t" | Out-Null; Write-Host "[완료] $t 삭제" } }
    foreach ($i in $indexes) { Sql "ALTER TABLE event DROP INDEX ``$i``" | Out-Null; Write-Host "[완료] 인덱스 $i 삭제" }
    if ($hasKey) { Sql 'ALTER TABLE event DROP COLUMN source_key' | Out-Null; Write-Host '[완료] source_key 칸 삭제' }
    if ($hasSource) { Sql 'ALTER TABLE event DROP COLUMN source' | Out-Null; Write-Host '[완료] source 칸 삭제' }
    Sql "DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0" | Out-Null
    Write-Host '[완료] 실패한 V2 기록 삭제'

    # ---------- 5. 결과 확인 ----------
    $events = One 'SELECT COUNT(*) FROM event'
    Write-Host ''
    Write-Host ('[확인] 재단 행사 ' + $events + '건 그대로')
    Write-Host '[다음] 앱을 다시 실행 → 로그에 Migrating schema ... to version "2" 와 Started FestivalApplication 이 나오면 성공'
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
