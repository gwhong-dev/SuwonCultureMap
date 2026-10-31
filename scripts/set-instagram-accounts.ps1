# set-instagram-accounts.ps1 — 내 PC 의 application-dev.properties 에 인스타 대상 계정 목록(instagram.accounts)을 넣는다
#  - 엔터만 치면 실측용 새 4계정 (suwon_city 는 이미 처리돼 숫자가 섞이므로 뺌)
#  - 실측이 끝나면 다시 실행해서 전체 목록(suwon_city 포함)으로 바꾸기
#  - 이미 있는 instagram.accounts 줄은 교체, 없으면 끝에 추가 (다른 줄은 그대로). 비밀값 없음
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$dev = 'src\main\resources\application-dev.properties'

$measure = 'sw_culture,suwonskartrium,suwontraditional,suwon_media'
$all     = 'suwon_city,' + $measure

Write-Host ''
Write-Host '===== 수원 문화지도 · 인스타 대상 계정 설정 (내 PC) ====='
Write-Host ''
if (-not (Test-Path -LiteralPath $dev)) {
    Write-Host '[오류] application-dev.properties 가 없음 → scripts\setup-dev.cmd 를 먼저 실행'
    exit 1
}
Write-Host ('  1 = 실측용 새 4계정  (' + $measure + ')')
Write-Host ('  2 = 전체 5계정       (' + $all + ')')
Write-Host '  또는 계정 이름을 쉼표로 직접 입력'
$in = (Read-Host '선택 (엔터 = 1)').Trim()
switch ($in) {
    ''  { $list = $measure }
    '1' { $list = $measure }
    '2' { $list = $all }
    default { $list = $in }
}
$names = @($list -split ',' | ForEach-Object { $_.Trim().TrimStart('@') } | Where-Object { $_ -ne '' })
foreach ($n in $names) {
    if ($n -notmatch '^[A-Za-z0-9._]{1,30}$') {
        Write-Host ('[오류] 계정 이름 형식이 아님: ' + $n + ' → 아무것도 바꾸지 않음')
        exit 1
    }
}
if ($names.Count -eq 0) { Write-Host '[오류] 계정이 비어 있음'; exit 1 }
$value = ($names -join ',')

$lines = @(Get-Content -LiteralPath $dev -Encoding UTF8)
$has = $false
$out = @(foreach ($l in $lines) {
    if ($l -match '^\s*instagram\.accounts\s*[=:]') { $has = $true; 'instagram.accounts=' + $value; continue }
    $l
})
if (-not $has) { $out += 'instagram.accounts=' + $value }
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Join-Path $root $dev), [string[]]$out, $utf8)
Write-Host ('[완료] instagram.accounts=' + $value + ' (' + $(if ($has) { '교체' } else { '추가' }) + ')')
Write-Host '[다음] 앱을 (다시) 실행 → scripts\instagram-sync.cmd'
