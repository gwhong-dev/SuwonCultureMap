# set-instagram-dev.ps1 — 내 PC 의 application-dev.properties 에 인스타 토큰 · 내 계정 ID 를 넣는다
#  - 토큰은 입력할 때 화면에 안 보이고(*), 저장 후에도 출력하지 않음
#  - 이미 있는 instagram.access-token / instagram.user-id 줄은 새 값으로 바꾸고, 없으면 끝에 추가 (다른 줄은 그대로)
#  - 형식 검사: 토큰 = 영문 · 숫자 · _ - . | 만 (InstagramClient 와 같은 규칙), 계정 ID = 숫자 5~30자리
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$dev = 'src\main\resources\application-dev.properties'

Write-Host ''
Write-Host '===== 수원 문화지도 · 인스타 수집 설정 (내 PC) ====='
Write-Host ''

if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}

$userId = (Read-Host '내 인스타 계정 ID (17841 로 시작하는 숫자)').Trim()
if ($userId -notmatch '^[0-9]{5,30}$') {
    Write-Host '[오류] 계정 ID 는 숫자만 5~30자리 → 아무것도 바꾸지 않음'
    exit 1
}

$secure = Read-Host '액세스 토큰 (붙여넣기 후 Enter, 화면에 안 보임)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim()
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if ($token -notmatch '^[A-Za-z0-9_\-.|]+$') {
    Write-Host '[오류] 토큰에 허용되지 않는 글자(공백 · 줄바꿈 등)가 있거나 비어 있음 → 아무것도 바꾸지 않음'
    exit 1
}

$lines = @(Get-Content -LiteralPath $dev -Encoding UTF8)
$hasToken = $false
$hasId = $false
$out = foreach ($l in $lines) {
    if ($l -match '^\s*instagram\.access-token\s*[=:]') { $hasToken = $true; 'instagram.access-token=' + $token; continue }
    if ($l -match '^\s*instagram\.user-id\s*[=:]') { $hasId = $true; 'instagram.user-id=' + $userId; continue }
    $l
}
$out = @($out)
if (-not $hasToken) { $out += 'instagram.access-token=' + $token }
if (-not $hasId) { $out += 'instagram.user-id=' + $userId }

# BOM 없는 UTF-8 로 저장 (Spring 이 읽는 형식 그대로)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Join-Path $root $dev), [string[]]$out, $utf8)
$token = $null
Write-Host ('[완료] 저장함 (토큰 ' + $(if ($hasToken) { '교체' } else { '추가' }) + ', 계정 ID ' + $(if ($hasId) { '교체' } else { '추가' }) + '). 토큰 값은 출력하지 않음')
Write-Host '[다음] 앱을 (다시) 실행한 뒤 인스타 동기화를 실행'
