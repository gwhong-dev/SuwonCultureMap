# setup-docker.ps1 — application-dev.properties 를 읽어 docker compose 용 .env 를 만든다
#  - 비밀값은 화면에 출력하지 않음 (키 이름만 출력)
#  - .env 가 이미 있으면 덮어쓰지 않음
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$dev     = 'src\main\resources\application-dev.properties'
$envFile = Join-Path $root '.env'

# application-dev.properties 를 Java properties 규칙대로 읽어 키→값 표로 돌려줌
#  - 주석(# !) · 빈 줄 제외, 구분자는 첫 번째 이스케이프 안 된 = : 또는 공백
#  - 줄 끝 \ (홀수 개) → 다음 줄과 이어붙임 (다음 줄 앞 공백 제거)
#  - \= \: \# \! \\ \  → 그 글자, \t \n \r \f → 제어문자, \uXXXX → 유니코드 글자
#  - 값 끝 공백은 지움 (이스케이프한 \  공백은 유지)
function Read-JavaProperties([string]$path) {
    $result = @{}
    $natural = @(Get-Content -LiteralPath $path -Encoding UTF8)
    $i = 0
    while ($i -lt $natural.Count) {
        $line = ([string]$natural[$i]).TrimStart(" `t`f"); $i++
        if ($line.Length -eq 0 -or $line[0] -eq '#' -or $line[0] -eq '!') { continue }
        # 이어쓰기: 끝의 \ 개수가 홀수면 마지막 \ 를 떼고 다음 줄을 붙임
        while ($true) {
            $n = 0; $k = $line.Length - 1
            while ($k -ge 0 -and $line[$k] -eq '\') { $n++; $k-- }
            if ($n % 2 -eq 0) { break }
            $line = $line.Substring(0, $line.Length - 1)
            if ($i -ge $natural.Count) { break }
            $line += ([string]$natural[$i]).TrimStart(" `t`f"); $i++
        }
        # 키 끝 찾기
        $p = 0
        while ($p -lt $line.Length) {
            $c = $line[$p]
            if ($c -eq '\') { $p += 2; continue }
            if ($c -eq '=' -or $c -eq ':' -or $c -eq ' ' -or $c -eq "`t" -or $c -eq "`f") { break }
            $p++
        }
        if ($p -gt $line.Length) { $p = $line.Length }
        $rawKey = $line.Substring(0, $p)
        # 구분자: 공백* [= 또는 :]? 공백*
        while ($p -lt $line.Length -and (' ', "`t", "`f") -contains [string]$line[$p]) { $p++ }
        if ($p -lt $line.Length -and ($line[$p] -eq '=' -or $line[$p] -eq ':')) { $p++ }
        while ($p -lt $line.Length -and (' ', "`t", "`f") -contains [string]$line[$p]) { $p++ }
        $key = (Convert-PropertiesEscape $rawKey).Value
        $val = Convert-PropertiesEscape $line.Substring($p)
        # 값 끝 공백 제거 (이스케이프한 글자 위치 이후는 유지)
        $v = $val.Value
        $end = $v.Length
        while ($end -gt $val.Keep -and (' ', "`t", "`f") -contains [string]$v[$end - 1]) { $end-- }
        $result[$key] = $v.Substring(0, $end)
    }
    return $result
}

# 이스케이프 해석. Keep = 마지막 이스케이프 글자 다음 위치 (끝 공백 제거의 하한)
function Convert-PropertiesEscape([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    $keep = 0
    $j = 0
    while ($j -lt $s.Length) {
        $c = $s[$j]
        if ($c -ne '\') { [void]$sb.Append($c); $j++; continue }
        $j++
        if ($j -ge $s.Length) { break }
        $e = $s[$j]; $j++
        switch -CaseSensitive ([string]$e) {
            't' { [void]$sb.Append("`t") }
            'n' { [void]$sb.Append("`n") }
            'r' { [void]$sb.Append("`r") }
            'f' { [void]$sb.Append("`f") }
            'u' {
                $hex = if ($j + 4 -le $s.Length) { $s.Substring($j, 4) } else { '' }
                if ($hex -notmatch '^[0-9A-Fa-f]{4}$') { throw ('properties 의 \u 표기가 잘못됨 (\uXXXX 형식 아님)') }
                [void]$sb.Append([char][Convert]::ToInt32($hex, 16)); $j += 4
            }
            default { [void]$sb.Append($e) }
        }
        $keep = $sb.Length
    }
    return [pscustomobject]@{ Value = $sb.ToString(); Keep = $keep }
}

Write-Host ''
Write-Host '===== 수원 문화지도 · Docker 준비 (.env 생성) ====='
Write-Host ''

if (Test-Path -LiteralPath $envFile) {
    # 이미 있는 .env 는 덮어쓰지 않음 (DB 비밀번호가 바뀌면 기존 MySQL 볼륨에 접속 못 함)
    # 단, 나중에 생긴 선택 항목(카카오 어드민 · 인스타 · 경기도 정류소)이 dev 파일에 있고 .env 에 없으면 그 줄만 끝에 보충
    $added = @()
    if (Test-Path -LiteralPath $dev) {
        $devProps = Read-JavaProperties $dev
        $existing = Get-Content -LiteralPath $envFile -Encoding UTF8
        $supplement = [ordered]@{ KAKAO_ADMIN_KEY = 'kakao.admin-key'; INSTAGRAM_ACCESS_TOKEN = 'instagram.access-token'; INSTAGRAM_USER_ID = 'instagram.user-id'; INSTAGRAM_ACCOUNTS = 'instagram.accounts'; GG_BUS_SERVICE_KEY = 'gg.bus.service-key' }
        foreach ($e in $supplement.GetEnumerator()) {
            $inEnv = @($existing | Where-Object { $_ -match ('^\s*' + $e.Key + '\s*=') }).Count -gt 0
            $v = $devProps[$e.Value]
            if (-not $inEnv -and -not [string]::IsNullOrWhiteSpace($v)) {
                # 값에 작은따옴표가 없으면 작은따옴표로 감쌈 (토큰 · 숫자 ID 는 해당 없음)
                if ($v -match "'") { Write-Host ('[건너뜀] ' + $e.Key + ' 값 형식이 예상과 다름'); continue }
                Add-Content -LiteralPath $envFile -Value ($e.Key + "='" + $v + "'") -Encoding UTF8
                $added += $e.Key
            }
        }
    }
    if ($added.Count -gt 0) { Write-Host ('[보충] .env 에 추가한 항목: ' + ($added -join ', ') + ' (값은 출력 안 함, 나머지 줄은 그대로)') }
    else { Write-Host '[건너뜀] .env 가 이미 있음 - 덮어쓰지 않음 (새로 만들려면 .env 를 지우고 다시 실행)' }
    exit 0
}
if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}

# Java properties 규칙대로 읽음 (이스케이프 · 줄 이어쓰기 포함)
$props = Read-JavaProperties $dev

# .env 이름 ← dev 파일 키
$map = [ordered]@{
    SWCF_API_KEY_EVENT   = 'swcf.api.key.event'
    KAKAO_REST_API_KEY   = 'kakao.rest-api-key'
    KAKAO_CLIENT_SECRET  = 'kakao.client-secret'
    GOOGLE_CLIENT_ID     = 'google.client-id'
    GOOGLE_CLIENT_SECRET = 'google.client-secret'
    NAVER_CLIENT_ID      = 'naver.client-id'
    NAVER_CLIENT_SECRET  = 'naver.client-secret'
}

$missing = @()
foreach ($e in $map.GetEnumerator()) {
    if (-not $props.ContainsKey($e.Value) -or [string]::IsNullOrWhiteSpace($props[$e.Value])) { $missing += $e.Value }
}
if ($missing.Count -gt 0) {
    Write-Host ('[오류] dev 파일에 값이 없는 키: ' + ($missing -join ', '))
    exit 1
}

# Docker 안 MySQL 은 새 DB 이므로 비밀번호를 무작위로 새로 만듦 (영문+숫자 24자)
function New-Password {
    $chars = [char[]]((48..57) + (65..90) + (97..122))
    -join (1..24 | ForEach-Object { $chars | Get-Random })
}

# .env 값을 따옴표로 감싸서 compose 가 그대로 읽게 함
#  - 작은따옴표: $ 변수 해석·' #' 주석 처리가 모두 꺼짐 (보통 이쪽)
#  - 값에 작은따옴표가 있으면 큰따옴표로 감싸고 \ " $ 를 이스케이프
function Escape-Value([string]$v) {
    if ($v -notmatch "'") { return "'" + $v + "'" }
    $e = $v -replace '\\', '\\' -replace '"', '\"' -replace '\$', '$$$$'
    return '"' + $e + '"'
}

$lines = @(
    '# docker compose 용 비밀값 - Git에 올라가지 않음 (scripts\setup-docker.cmd 가 생성)'
    'APP_BASE_URL=http://localhost:8080'
    'MYSQL_DATABASE=suwon_culture_map'
    'DB_USERNAME=suwon'
    ('DB_PASSWORD=' + (New-Password))
    ('MYSQL_ROOT_PASSWORD=' + (New-Password))
)
foreach ($e in $map.GetEnumerator()) { $lines += ($e.Key + '=' + (Escape-Value $props[$e.Value])) }
# 카카오 어드민 키 · 인스타 수집 · 경기도 정류소 키는 선택 → dev 파일에 값이 있을 때만 넣음 (없으면 compose 기본값)
$optional = [ordered]@{
    KAKAO_ADMIN_KEY        = 'kakao.admin-key'
    INSTAGRAM_ACCESS_TOKEN = 'instagram.access-token'
    INSTAGRAM_USER_ID      = 'instagram.user-id'
    INSTAGRAM_ACCOUNTS     = 'instagram.accounts'
    GG_BUS_SERVICE_KEY     = 'gg.bus.service-key'
}
foreach ($e in $optional.GetEnumerator()) {
    if ($props.ContainsKey($e.Value) -and -not [string]::IsNullOrWhiteSpace($props[$e.Value])) {
        $lines += ($e.Key + '=' + (Escape-Value $props[$e.Value]))
    }
}

[System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding $false))
Write-Host '[완료] .env 생성'

# Git 에 올라가지 않는지 확인
git check-ignore -q .env
if ($LASTEXITCODE -eq 0) { Write-Host '[완료] .env 는 Git 제외 대상 (커밋 안 됨)' }
else { Write-Host '[경고] .env 가 Git 제외 대상이 아님 - 커밋하지 말고 알려주세요' }

Write-Host ''
Write-Host '.env 키 목록 - 값은 출력하지 않음'
foreach ($l in $lines) { if ($l -match '^([A-Z_]+)=') { Write-Host ('    - ' + $Matches[1]) } }
Write-Host ''
Write-Host '다음: docker compose up --build'
