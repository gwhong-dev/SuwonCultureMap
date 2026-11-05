# jenkins-start.ps1 — 내 PC Docker 에 Jenkins 를 띄운다 (docs/jenkins.md)
#  - 처음이면 jenkins\.env 에 관리자 비밀번호를 무작위로 만듦 (Git 제외, 이 PC 에만 있음)
#  - 이미 떠 있으면 그대로 둠 (설정 · 빌드 기록은 jenkins-home 볼륨에 보관)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$envFile = Join-Path $root 'jenkins\.env'
$url     = 'http://localhost:8081'

Write-Host ''
Write-Host '===== 수원 문화지도 · 로컬 Jenkins 켜기 ====='
Write-Host ''

# 1) Docker Desktop 이 켜져 있는지
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host '[오류] docker 명령이 없음 → Docker Desktop 설치 후 다시 실행'
    exit 1
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host '[오류] Docker 가 꺼져 있음 → 시작 메뉴에서 Docker Desktop 을 켜고, 고래 아이콘이 멈추면 다시 실행'
    exit 1
}
Write-Host '[확인] Docker 켜져 있음'

# 2) 관리자 비밀번호 파일 (없을 때만 만듦)
if (-not (Test-Path -LiteralPath $envFile)) {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'.ToCharArray()
    $pw = -join (1..20 | ForEach-Object { $chars | Get-Random })
    # BOM 없는 UTF-8 (docker compose 가 첫 줄을 바로 읽게)
    [IO.File]::WriteAllText($envFile, "JENKINS_ADMIN_PASSWORD=$pw`n", (New-Object Text.UTF8Encoding($false)))
    Write-Host '[생성] jenkins\.env (관리자 비밀번호, Git 에 올라가지 않음)'
} else {
    Write-Host '[확인] jenkins\.env 있음 - 그대로 사용'
}

# 3) 이미지 빌드 + 실행 (처음엔 플러그인 설치로 몇 분 걸림)
Write-Host '[실행] docker compose up -d --build (처음엔 3~5분)'
docker compose -f jenkins\docker-compose.yml --env-file $envFile up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host '[오류] Jenkins 컨테이너를 띄우지 못함 → 위 메시지 확인 (8081 포트를 다른 프로그램이 쓰는지 등)'
    exit 1
}

# 4) 로그인 화면이 열릴 때까지 기다림 (최대 3분)
Write-Host -NoNewline '[대기] Jenkins 시작 중'
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$url/login" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { }
    Write-Host -NoNewline '.'
    Start-Sleep -Seconds 3
}
Write-Host ''
if (-not $ok) {
    Write-Host '[오류] 3분 안에 응답 없음 → 로그 확인: docker compose -f jenkins\docker-compose.yml logs jenkins'
    exit 1
}

$pw = ((Get-Content -LiteralPath $envFile -Encoding UTF8 | Where-Object { $_ -match '^JENKINS_ADMIN_PASSWORD=' }) -replace '^JENKINS_ADMIN_PASSWORD=', '')
Write-Host ''
Write-Host "[성공] $url"
Write-Host '       아이디 : admin'
Write-Host "       비밀번호: $pw   (jenkins\.env 에 저장됨)"
Write-Host '       작업    : SuwonCultureMap → "파라미터와 함께 빌드" → BRANCH=main → 빌드'
Write-Host '       첫 빌드는 Maven 라이브러리를 받느라 5~10분, 두 번째부터 빨라짐'
Write-Host '       끄기    : scripts\jenkins-stop.cmd'
Start-Process $url
