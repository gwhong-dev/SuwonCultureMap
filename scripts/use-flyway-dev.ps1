# use-flyway-dev.ps1 — 내 PC 의 application-dev.properties 를 Flyway 방식으로 바꾼다
#  - spring.jpa.hibernate.ddl-auto=update  →  validate  (한 줄만 바꿈, 다른 줄 · 비밀값은 그대로)
#  - 비밀값은 화면에 출력하지 않음
#  - 이미 validate 면 아무것도 안 함
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$dev = 'src\main\resources\application-dev.properties'

Write-Host ''
Write-Host '===== 수원 문화지도 · 개발 설정을 Flyway 방식으로 ====='
Write-Host ''

if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}

$lines = Get-Content -LiteralPath $dev -Encoding UTF8
$pattern = '^\s*spring\.jpa\.hibernate\.ddl-auto\s*[=:]\s*(\S+)\s*$'
$found = $false
$changed = $false
$out = foreach ($l in $lines) {
    if ($l -match $pattern) {
        $found = $true
        if ($Matches[1] -ne 'validate') {
            Write-Host ('[변경] ddl-auto = ' + $Matches[1] + '  →  validate')
            $changed = $true
            'spring.jpa.hibernate.ddl-auto=validate'
            continue
        }
    }
    $l
}

if (-not $found) {
    # 줄이 없으면 공통 설정(validate)을 그대로 따름 → 바꿀 것 없음
    Write-Host '[확인] ddl-auto 줄이 없음 → 공통 설정(validate)을 따름. 바꿀 것 없음'
    exit 0
}
if (-not $changed) {
    Write-Host '[확인] 이미 validate → 바꿀 것 없음'
    exit 0
}

# BOM 없는 UTF-8 로 저장 (Spring 이 읽는 형식 그대로)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Join-Path $root $dev), [string[]]$out, $utf8)
Write-Host '[완료] 저장함. 이제 앱을 실행하면 Flyway 가 DB 를 확인합니다'
