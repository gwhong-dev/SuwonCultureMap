# setup-docker.ps1 — application-dev.properties 를 읽어 docker compose 용 .env 를 만든다
#  - 비밀값은 화면에 출력하지 않음 (키 이름만 출력)
#  - .env 가 이미 있으면 덮어쓰지 않음
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$dev     = 'src\main\resources\application-dev.properties'
$envFile = Join-Path $root '.env'

Write-Host ''
Write-Host '===== 수원 문화지도 · Docker 준비 (.env 생성) ====='
Write-Host ''

if (Test-Path -LiteralPath $envFile) {
    Write-Host '[건너뜀] .env 가 이미 있음 - 덮어쓰지 않음 (새로 만들려면 .env 를 지우고 다시 실행)'
    exit 0
}
if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}

# key=value / key: value 형식만 읽음 (주석·빈 줄 제외)
$props = @{}
foreach ($l in Get-Content -LiteralPath $dev -Encoding UTF8) {
    if ($l -match '^\s*([^#!\s=:][^=:]*?)\s*[=:]\s*(.*)$') {
        $props[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

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
# 인스타 수집은 선택 → dev 파일에 값이 있을 때만 넣음 (없으면 compose 기본값 = 빈 값 → 수집 안 함)
$optional = [ordered]@{
    INSTAGRAM_ACCESS_TOKEN = 'instagram.access-token'
    INSTAGRAM_USER_ID      = 'instagram.user-id'
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
