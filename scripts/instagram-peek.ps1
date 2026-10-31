# instagram-peek.ps1 — 인스타 공식 API(Business Discovery)로 대상 계정의 최근 게시물을 조회해 "화면에만" 보여준다
#  - 목적: 캡션 정리기(PR 1)를 만들기 전에 실제 캡션이 어떤 모양인지 눈으로 확인하고,
#          5개 필드(id · caption · permalink · timestamp · media_type)가 오는지 확인 (다른 계정 확인에도 사용)
#  - 토큰은 입력창(화면에 안 보임)으로 받아 메모리에만 두고, 주소(URL) · 화면 · 파일 어디에도 남기지 않음 (Authorization 헤더로 전송)
#  - 캡션은 화면에만 출력. 파일로 저장하지 않음 (실제 캡션은 저장소에 커밋 금지 — docs/instagram-integration.md 1번)
#  - 환경변수로 미리 넣어 둘 수도 있음: INSTAGRAM_ACCESS_TOKEN, INSTAGRAM_USER_ID, INSTAGRAM_GRAPH_VERSION (기본 v25.0)
param(
    [string]$Username,
    [int]$Limit = 5
)
$ErrorActionPreference = 'Stop'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

# 입력창(별표 표시)으로 비밀값을 받아 문자열로 바꿈 — 화면에 출력하지 않음
function Read-Secret([string]$prompt) {
    $sec  = Read-Host -Prompt $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try     { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# 인스타가 주는 UTC 시간 → 한국 시간 (못 바꾸면 원문 그대로)
function Format-Kst($ts) {
    try {
        $inv = [Globalization.CultureInfo]::InvariantCulture
        if ($ts -is [datetime]) { $dto = [DateTimeOffset]$ts } else { $dto = [DateTimeOffset]::Parse([string]$ts, $inv) }
        return $dto.ToOffset([TimeSpan]::FromHours(9)).ToString('yyyy-MM-dd HH:mm', $inv) + ' (한국)'
    } catch { return [string]$ts }
}

# 오류 문구에 토큰이 섞여 나와도 화면에 찍히지 않게 가림 (입력한 토큰 + EAA 로 시작하는 토큰 모양)
function Hide-Secret([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return $text }
    if (-not [string]::IsNullOrEmpty($token)) { $text = $text.Replace($token, '***') }
    return ($text -replace 'EAA[A-Za-z0-9]+', '***')
}

# 바깥에서 온 글(캡션 · 링크)에서 제어문자만 제거 — 줄바꿈 · 탭은 유지
function Remove-ControlChars([string]$text) {
    return ($text -replace '[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]', '')
}

Write-Host ''
Write-Host '===== 인스타 캡션 확인 (화면에만 출력 · 저장 안 함) ====='
Write-Host ''

# 1) 입력 확인 — 형식이 틀리면 요청을 보내기 전에 멈춤
$igId = $env:INSTAGRAM_USER_ID
if ([string]::IsNullOrWhiteSpace($igId)) {
    Write-Host '(계정 ID = 토큰 점검 결과의 granular_scopes 에 있는 인스타 계정 숫자 · docs/instagram-integration.md 3번)'
    $igId = Read-Host '내 인스타 계정 ID (숫자)'
}
$igId = ([string]$igId).Trim()
if ($igId -notmatch '^[0-9]+$') {
    Write-Host '[오류] 계정 ID는 숫자만 입력해야 함 → 다시 실행'
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Read-Host '조회할 계정 이름 (엔터 = suwon_city)'
    if ([string]::IsNullOrWhiteSpace($Username)) { $Username = 'suwon_city' }
}
$Username = $Username.Trim().TrimStart('@')
if ($Username -notmatch '^[A-Za-z0-9._]{1,30}$') {
    Write-Host '[오류] 계정 이름은 영문 · 숫자 · 마침표 · 밑줄만 가능 → 다시 실행'
    exit 1
}
if ($Limit -lt 1 -or $Limit -gt 25) {
    Write-Host '[오류] 게시물 개수는 1~25 → 다시 실행'
    exit 1
}

$ver = $env:INSTAGRAM_GRAPH_VERSION
if ([string]::IsNullOrWhiteSpace($ver)) { $ver = 'v25.0' }
if ($ver -notmatch '^v\d+\.\d+$') {
    Write-Host '[오류] INSTAGRAM_GRAPH_VERSION 형식은 v25.0 처럼 → 다시 실행'
    exit 1
}

$token = $env:INSTAGRAM_ACCESS_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host '(토큰은 방금 발급한 것을 쓰기 — Graph API 탐색기의 단기 토큰은 약 1시간 뒤 만료)'
    $token = Read-Secret '액세스 토큰 (붙여넣고 엔터 · 화면에 안 보임)'
}
$token = ([string]$token).Trim()
# 토큰에 쓰이는 문자만 허용 — 줄바꿈 · 한글 등이 섞여 헤더 오류 문구로 번지는 것을 요청 전에 차단
if ([string]::IsNullOrWhiteSpace($token) -or $token -notmatch '^[A-Za-z0-9_\-\.\|]+$') {
    Write-Host '[오류] 토큰이 비었거나 허용되지 않는 문자(공백 · 줄바꿈 · 한글 등)가 있음 → 토큰만 다시 복사해서 실행'
    exit 1
}

# 2) 요청 — 토큰은 주소가 아니라 헤더로 보냄
$fields = 'business_discovery.username(' + $Username + '){media.limit(' + $Limit + '){id,caption,permalink,timestamp,media_type}}'
$uri    = 'https://graph.facebook.com/' + $ver + '/' + $igId + '?fields=' + [Uri]::EscapeDataString($fields)

Write-Host ''
Write-Host ('조회 중... 대상 @' + $Username + ' · 최근 ' + $Limit + '개 · API ' + $ver)

try {
    # 글자 깨짐 방지: 응답을 바이트로 받아 UTF-8 로 직접 해석 (Windows PowerShell 5.1 대비)
    $resp = Invoke-WebRequest -Uri $uri -Headers @{ Authorization = ('Bearer ' + $token) } -Method Get -UseBasicParsing
    $json = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
}
catch {
    $body = $null
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        $body = $_.ErrorDetails.Message
    } elseif ($_.Exception.Response) {
        try {
            $sr   = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream(), [Text.Encoding]::UTF8)
            $body = $sr.ReadToEnd()
        } catch {}
    }
    $err = $null
    if ($body) { try { $err = ($body | ConvertFrom-Json).error } catch {} }

    Write-Host ''
    Write-Host '[실패] 요청이 거절됐거나 연결에 실패함'
    if ($err) {
        Write-Host ('  Meta 응답: code={0} subcode={1} type={2}' -f $err.code, $err.error_subcode, $err.type)
        Write-Host ('  메시지: ' + (Hide-Secret ([string]$err.message)))
        $code = [int]$err.code
        if ($code -eq 190) {
            Write-Host '  → 토큰이 만료됐거나 잘못됐을 수 있음. 새 토큰으로도 같으면 헤더 방식(Authorization) 전달이 거부된 것일 수 있으니 이 화면 내용을 알려주기'
        } elseif ($code -in 4, 17, 32, 613) {
            Write-Host '  → 호출 제한에 걸림. 잠시 뒤 다시 실행'
        } elseif ($code -eq 10 -or ($code -ge 200 -and $code -le 299)) {
            Write-Host '  → 권한 부족. 앱 권한 4개와 토큰 발급 때 허용한 페이지 · 계정 확인'
        } else {
            Write-Host '  → 가능한 원인: 대상이 프로페셔널(비즈니스 · 크리에이터) 계정이 아님 / 계정 이름 오타 / 내 계정 ID 오류'
        }
    } else {
        Write-Host ('  ' + (Hide-Secret $_.Exception.Message))
    }
    exit 1
}
finally {
    $token = $null
}

# 3) 결과 — 필드 수신 요약 + 게시물별 내용
$data = $json | ConvertFrom-Json
if (-not $data.business_discovery) {
    Write-Host ''
    Write-Host '[알림] 응답에 business_discovery 항목이 없음 → 응답 형식이 예상과 다름. 계정 이름 · 권한을 확인'
    exit 1
}
$posts = @()
if ($data.business_discovery.media.data) { $posts = @($data.business_discovery.media.data) }

if ($posts.Count -eq 0) {
    Write-Host ''
    Write-Host '[알림] 받은 게시물이 0개 → 대상이 프로페셔널(비즈니스 · 크리에이터) 계정인지, 연령 제한 계정은 아닌지 확인'
    exit 0
}

Write-Host ''
Write-Host ('받은 게시물: ' + $posts.Count + '개 · 필드별로 값이 있는 게시물 수')
foreach ($n in @('id', 'caption', 'permalink', 'timestamp', 'media_type')) {
    $c = @($posts | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.$n) }).Count
    Write-Host ('  {0,-11} {1}/{2}' -f $n, $c, $posts.Count)
}

$i = 0
foreach ($p in $posts) {
    $i++
    Write-Host ''
    Write-Host ('━━━━━ {0}/{1} ━━━━━' -f $i, $posts.Count)
    Write-Host ('종류: {0}   시간: {1}' -f $p.media_type, (Format-Kst $p.timestamp))
    Write-Host ('링크: ' + (Remove-ControlChars ([string]$p.permalink)))
    Write-Host '[캡션]'
    if ([string]::IsNullOrWhiteSpace([string]$p.caption)) { Write-Host '(캡션 없음)' } else { Write-Host (Remove-ControlChars ([string]$p.caption)) }
}

Write-Host ''
Write-Host '※ 캡션은 이 화면에만 있음. 파일로 저장하거나 저장소에 붙이지 말 것 (저작권 원칙).'
Write-Host '※ 확인이 끝나면 이 창을 닫기 (입력한 토큰은 창을 닫으면 사라짐).'
