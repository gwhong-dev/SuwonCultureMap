# instagram-sync.ps1 — 실행 중인 내 PC 앱(localhost)에 인스타 동기화(POST /api/instagram/sync)를 1번 요청하고 결과 숫자만 보여 준다
#  - 결과에는 개수 · 오류 종류만 있음 (캡션 원문 · 토큰 없음)
param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

# 앱은 src 원본이 아니라 켤 때 복사된 target\classes 설정을 읽음
#  → 토큰 줄이 서로 다르면 앱이 옛 토큰으로 켜져 있는 것 (값은 비교만 하고 출력 X)
function Get-TokenLine([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    return @(Get-Content -LiteralPath $path -Encoding UTF8 | Where-Object { $_ -match '^\s*instagram\.access-token\s*[=:]' }) -join "`n"
}
$root = Split-Path -Parent $PSScriptRoot
$srcLine = Get-TokenLine (Join-Path $root 'src\main\resources\application-dev.properties')
$runLine = Get-TokenLine (Join-Path $root 'target\classes\application-dev.properties')
$staleConfig = ($null -ne $srcLine) -and ($null -ne $runLine) -and ($srcLine -ne $runLine)
$srcLine = $null; $runLine = $null

Write-Host ''
if ($staleConfig) {
    Write-Host '⚠️ 앱이 저장 전 토큰으로 켜져 있음 (target\classes 설정이 옛것)'
    Write-Host '   → 앱 끄기(Ctrl+C) → ./mvnw spring-boot:run 으로 다시 켠 뒤 이 스크립트 재실행'
    Write-Host ''
}
Write-Host ('===== 인스타 동기화 실행 (http://localhost:' + $Port + ') =====')
Write-Host '(계정 수 · 게시물 수에 따라 1~2분 걸릴 수 있음)'
try {
    # Windows PowerShell 5.1 의 Invoke-RestMethod 는 charset 없는 JSON 을 ISO-8859-1 로 읽어 한글이 깨짐
    #  → 응답 바이트를 직접 UTF-8 로 풀고 JSON 변환
    $w = Invoke-WebRequest -UseBasicParsing -Method Post -Uri ('http://localhost:' + $Port + '/api/instagram/sync') -TimeoutSec 600
    $r = [Text.Encoding]::UTF8.GetString($w.RawContentStream.ToArray()) | ConvertFrom-Json
} catch {
    Write-Host ('[오류] 요청 실패: ' + $_.Exception.Message)
    Write-Host '→ 앱이 켜져 있나요? IntelliJ 실행 창에 "Started FestivalApplication" 이 보여야 함'
    Write-Host '→ Docker(운영 프로필) 앱에는 이 API 가 없음 (자동 동기화만 실행)'
    exit 1
}
Write-Host ''
if ($r.tokenWarning) { Write-Host ('⚠️ ' + $r.tokenWarning); Write-Host '' }
if (-not $r.configured) { Write-Host ('[설정 안 됨] ' + $r.message); exit 1 }
Write-Host ('계정      : ' + $r.accountsOk + ' / ' + $r.accounts + ' 성공')
foreach ($e in @($r.accountErrors)) { Write-Host ('  ❌ ' + $e) }
if (@($r.accountErrors) -match 'TOKEN_INVALID') {
    Write-Host '  → 토큰 무효(code 190): ① set-instagram-dev 실행 후 앱을 다시 켰는지 ② 60일 만료 → set-instagram-dev 재실행'
}
Write-Host ('게시물    : ' + $r.posts + ' (새 게시물 ' + $r.newPosts + ', 오류 ' + $r.postErrors + ')')
Write-Host '블록 상태 :'
foreach ($p in $r.blocks.PSObject.Properties) { Write-Host ('  ' + $p.Name.PadRight(10) + $p.Value) }
if ($r.merge) {
    if ($r.merge.ok) { Write-Host ('합치기    : 같은 행사 묶음 ' + $r.merge.groupCount + ' · 숨김 ' + $r.merge.hiddenCount + ' · 충돌 ' + $r.merge.conflictCount) }
    else { Write-Host '⚠️ 합치기  : 실패 (동기화 결과는 저장됨, 다음 동기화 때 다시 계산)' }
}
Write-Host ('행사      : 새로 저장 ' + $r.eventsSaved + ' · 갱신 ' + $r.eventsUpdated + ' · 중복 블록 ' + $r.duplicateBlocks)
Write-Host ''
Write-Host '→ 위 화면을 캡처하거나 숫자만 알려 주세요'
