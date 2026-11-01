# set-instagram-dev.ps1 — 내 PC 의 application-dev.properties 에 인스타 토큰 · 내 계정 ID 를 넣는다
#  - 탐색기에서 받은 단기 토큰(1~2시간)을 Meta 에 보내 장기 토큰(약 60일)으로 바꿔서 저장 (기본)
#    · 교환에 필요한 앱 시크릿은 매번 입력받고 어디에도 저장 · 출력하지 않음
#    · 만료일(instagram.token-expires-at, YYYY-MM-DD)을 같이 저장 → 앱이 만료 7일 전부터 동기화 결과에 알림
#  - N 을 고르면 예전처럼 단기 토큰을 그대로 저장 (만료일은 비움)
#  - 토큰은 입력할 때 화면에 안 보이고(*), 저장 후에도 출력하지 않음
#  - 이미 있는 instagram.* 줄은 새 값으로 바꾸고, 없으면 끝에 추가 (다른 줄은 그대로)
#  - 형식 검사: 토큰 = 영문 · 숫자 · _ - . | 만 (InstagramClient 와 같은 규칙), 계정 ID · 앱 ID = 숫자
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$dev = 'src\main\resources\application-dev.properties'
$graph = 'https://graph.facebook.com/v25.0'

function Read-Secret([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function Get-Current([string[]]$lines, [string]$key) {
    foreach ($l in $lines) {
        if ($l -match ('^\s*' + [regex]::Escape($key) + '\s*[=:]\s*(.*)$')) { return $Matches[1].Trim() }
    }
    return ''
}

Write-Host ''
Write-Host '===== 수원 문화지도 · 인스타 수집 설정 (내 PC) ====='
Write-Host ''

if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}
$lines = @(Get-Content -LiteralPath $dev -Encoding UTF8)

# 1) 내 인스타 계정 ID (이미 있으면 Enter 로 유지)
$oldId = Get-Current $lines 'instagram.user-id'
$hint = if ($oldId) { ' [Enter = 기존 값 유지]' } else { '' }
$userId = (Read-Host ('내 인스타 계정 ID (17841 로 시작하는 숫자)' + $hint)).Trim()
if (-not $userId) { $userId = $oldId }
if ($userId -notmatch '^[0-9]{5,30}$') {
    Write-Host '[오류] 계정 ID 는 숫자만 5~30자리 → 아무것도 바꾸지 않음'
    exit 1
}

# 2) 탐색기에서 받은 토큰
$token = Read-Secret '액세스 토큰 (탐색기에서 복사 → 붙여넣기 후 Enter, 화면에 안 보임)'
if ($token -notmatch '^[A-Za-z0-9_\-.|]+$') {
    Write-Host '[오류] 토큰에 허용되지 않는 글자(공백 · 줄바꿈 등)가 있거나 비어 있음 → 아무것도 바꾸지 않음'
    exit 1
}

# 3) 장기 토큰(60일)으로 교환
$appId = Get-Current $lines 'instagram.app-id'
$expiresAt = ''
$answer = (Read-Host '60일 장기 토큰으로 바꿔서 저장할까요? [Y/n]').Trim()
if ($answer -notmatch '^[nN]') {
    $hint = if ($appId) { ' [Enter = 기존 값 유지]' } else { '' }
    $inputId = (Read-Host ('Meta 앱 ID (developers.facebook.com → 내 앱 → 앱 설정 → 기본 설정 맨 위 숫자)' + $hint)).Trim()
    if ($inputId) { $appId = $inputId }
    if ($appId -notmatch '^[0-9]{5,30}$') {
        Write-Host '[오류] 앱 ID 는 숫자만 5~30자리 → 아무것도 바꾸지 않음'
        exit 1
    }
    $secret = Read-Secret '앱 시크릿 (같은 화면 "앱 시크릿 코드" → 표시 → 복사. 화면에 안 보임 · 저장 안 함)'
    if ($secret -notmatch '^[A-Za-z0-9]+$') {
        Write-Host '[오류] 앱 시크릿은 영문 · 숫자만 → 아무것도 바꾸지 않음'
        exit 1
    }
    Write-Host '[진행] Meta 에 장기 토큰 교환 요청 중...'
    try {
        $r = Invoke-RestMethod -UseBasicParsing -Method Get -Uri ($graph + '/oauth/access_token') -TimeoutSec 30 -Body @{
            grant_type        = 'fb_exchange_token'
            client_id         = $appId
            client_secret     = $secret
            fb_exchange_token = $token
        }
    } catch {
        # 오류 원문 · 요청 주소에는 비밀값이 섞일 수 있음 → Graph 오류 번호 · 종류만 출력
        $code = ''; $type = ''
        try { $e = ($_.ErrorDetails.Message | ConvertFrom-Json).error; $code = $e.code; $type = $e.type } catch {}
        $kind = $_.Exception.GetType().Name  # 예외 종류 이름만 (비밀값 없음) — 네트워크 · 프록시 오류면 code 가 비어 있음
        Write-Host ('[오류] 교환 실패 (Graph code=' + $code + ' ' + $type + ' / ' + $kind + ') → 아무것도 바꾸지 않음')
        if (-not $code) { Write-Host '  · code 빈칸 : 인터넷 연결 · 회사/학원 프록시 확인 후 다시 실행' }
        Write-Host '  · code 190 : 토큰이 이미 만료 → 탐색기에서 새로 발급 후 다시 실행'
        Write-Host '  · code 101 / 1 : 앱 ID · 시크릿이 틀림, 또는 탐색기 오른쪽 "Meta 앱" 칸이 다른 앱'
        exit 1
    } finally {
        $secret = $null
    }
    $long = "$($r.access_token)".Trim()
    if ($long -notmatch '^[A-Za-z0-9_\-.|]+$') {
        Write-Host '[오류] 응답에 토큰이 없거나 형식이 다름 → 아무것도 바꾸지 않음'
        exit 1
    }
    $token = $long
    $long = $null
    if ($r.expires_in -and [long]$r.expires_in -gt 0) {
        $expiresAt = (Get-Date).AddSeconds([long]$r.expires_in).ToString('yyyy-MM-dd')
    } else {
        Write-Host '[주의] 응답에 만료 시간이 없어 만료일을 비워 둠 (앱 알림 없음)'
    }
} else {
    Write-Host '[안내] 단기 토큰 그대로 저장 (1~2시간 뒤 만료, 만료일 알림 없음)'
}

# 4) 저장
$values = [ordered]@{
    'instagram.access-token'     = $token
    'instagram.user-id'          = $userId
    'instagram.token-expires-at' = $expiresAt
}
if ($appId) { $values['instagram.app-id'] = $appId }  # 앱 ID 는 비밀값 아님 (다음 교환 때 Enter 로 재사용)
$seen = @{}
$out = foreach ($l in $lines) {
    $hit = $null
    foreach ($k in $values.Keys) {
        if ($l -match ('^\s*' + [regex]::Escape($k) + '\s*[=:]')) { $hit = $k; break }
    }
    if ($hit) { $seen[$hit] = $true; $hit + '=' + $values[$hit]; continue }
    $l
}
$out = @($out)
foreach ($k in $values.Keys) { if (-not $seen[$k]) { $out += $k + '=' + $values[$k] } }

# BOM 없는 UTF-8 로 저장 (Spring 이 읽는 형식 그대로)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Join-Path $root $dev), [string[]]$out, $utf8)
$token = $null
$values = $null

Write-Host ''
if ($expiresAt) {
    Write-Host ('[완료] 장기 토큰 저장 · 만료일 ' + $expiresAt + ' (토큰 값은 출력하지 않음)')
    Write-Host '        만료 7일 전부터 인스타 동기화 결과에 알림이 나옴 → 그때 이 스크립트를 다시 실행'
} else {
    Write-Host '[완료] 토큰 저장 (토큰 값은 출력하지 않음)'
}
Write-Host '[다음] 앱이 켜져 있으면 끄고(Ctrl+C) 다시 실행 → scripts\instagram-sync.cmd 실행'
Write-Host '       (앱은 켤 때 복사한 설정을 읽어서, 켜 둔 채로는 새 토큰이 반영 안 됨)'
