# instagram-sync.ps1 — 실행 중인 내 PC 앱(localhost)에 인스타 동기화(POST /api/instagram/sync)를 1번 요청하고 결과 숫자만 보여 준다
#  - 결과에는 개수 · 오류 종류만 있음 (캡션 원문 · 토큰 없음)
param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

Write-Host ''
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
    exit 1
}
Write-Host ''
if (-not $r.configured) { Write-Host ('[설정 안 됨] ' + $r.message); exit 1 }
Write-Host ('계정      : ' + $r.accountsOk + ' / ' + $r.accounts + ' 성공')
foreach ($e in @($r.accountErrors)) { Write-Host ('  ❌ ' + $e) }
Write-Host ('게시물    : ' + $r.posts + ' (새 게시물 ' + $r.newPosts + ', 오류 ' + $r.postErrors + ')')
Write-Host '블록 상태 :'
foreach ($p in $r.blocks.PSObject.Properties) { Write-Host ('  ' + $p.Name.PadRight(10) + $p.Value) }
Write-Host ('행사      : 새로 저장 ' + $r.eventsSaved + ' · 갱신 ' + $r.eventsUpdated + ' · 중복 블록 ' + $r.duplicateBlocks)
Write-Host ''
Write-Host '→ 위 화면을 캡처하거나 숫자만 알려 주세요'
