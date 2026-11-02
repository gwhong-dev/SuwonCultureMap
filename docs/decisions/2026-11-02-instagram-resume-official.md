# 인스타그램 수집 재개 결정 (수원 공식 계정만)

- 결정일: 2026-11-02
- 결정한 사람: 사용자(운영자)
- 상태: 시행 중. 같은 날의 [수집 중단 결정](2026-11-02-instagram-scope-off.md)을 대체한다.

## 배경

중단 결정은 "Meta 의 공식 답을 받을 때까지 끈다"였다. 이후 Meta 원문을 직접 읽고, 공식 답을 받을 수 있는 경로가 있는지 확인했다.

### Meta 원문 확인 결과 (2026-11-02 열람)

Platform Terms · Developer Policies 는 2026-02-03 최종 수정본, 나머지는 열람 시점 게시본.

| 문서 · 조항 | 원문 요지 | 이 서비스 | 판정 |
|---|---|---|---|
| Developer Policies 6 (Instagram Platform) | "Don't use the Instagram Platform to simply display User Content, import or backup content … without our prior permission." | 사진 · 캡션을 그대로 보여주지 않고, 캡션에서 뽑은 행사 정보와 원문 링크만 표시 | 언급 없음 (해석 필요) |
| Developer Policies 6 | 게시물 소유자의 요구 · 제한을 따를 것, 책임은 개발자에게 | 소유자 요청 시 즉시 삭제 | 의무 |
| Platform Terms 3.a.viii | 개발자 문서의 허용 용도 밖 처리 금지 | Business Discovery 문서: "다른 프로 계정의 기본 정보 · 지표 조회", `instagram_manage_insights`: "다른 비즈니스 계정의 프로필 · 미디어를 읽을 수 있음". 공개 지도 표시는 목록에 없음 | 언급 없음 |
| Platform Terms 3.c | 제한 데이터가 아니면 제3자 공유 가능 (계약상 금지 조건) | 익명 방문자에게 보여주는 것이 "공유"인지 정의 없음 | 언급 없음 |
| Platform Terms 3.d | 요청 시 삭제, 필요 없어지면 삭제, 쉽게 찾을 수 있는 삭제 요청 방법 제공 | 처리방침 · 서비스 안내에 삭제 요청 연락처 | 의무 |
| Platform Terms 3.a.iv · v | 판매 금지, 동의 없는 프로필 구축 금지 | 해당 없음 | 의무 |
| Platform Terms 4.b | 처리방침에 처리 내용과 삭제 요청 방법 | `privacy.html` 10번 | 의무 |
| App Review 문서 · Access Levels | "My app is only for a business I own or manage → Standard Access → App Review Not required", 역할이 있는 사용자만 쓰면 Standard Access | 토큰은 운영자 계정 하나, 방문자는 Meta 로그인 없음, 앱 모드 개발 중 | 검수 불필요 |

- 명시적 허용도 명시적 금지도 없다. 중단 결정 문서에서 "접근 수준 때문에 검수가 필요할 수 있다"고 본 것은 틀렸고, 핵심 조항(Developer Policies 6)은 빠져 있었다.

### 공식 답을 받을 경로

| 경로 | 결과 |
|---|---|
| 개발자 지원 (developers.facebook.com/support 의 Get Help) | 버튼이 보이지 않음 (사용자 여러 차례 확인) |
| Direct Support (business.facebook.com/direct-support) | Meta 도움말 원문: "Not all people will have access to Direct Support." |
| 비즈니스 인증 + 앱 심사 | 사업자가 없어 비즈니스 인증 불가 |

→ 공식 판정을 받을 경로가 없다.

## 결정

Meta 원문을 근거로 직접 판단해 **수원시 · 문화기관 공식 계정에 한해** 수집을 재개한다.

| 항목 | 내용 |
|---|---|
| 대상 | 수원시 · 문화기관 공식 계정 (`suwon_city`, `sw_culture`, `suwonskartrium`, `suwontraditional`, `suwon_media`) |
| 제외 | 개인(인플루언서) 계정. 개인정보보호법 검토 전에는 넣지 않음 |
| 저장 · 표시 범위 | 캡션에서 뽑은 행사 제목 · 일시 · 시간 · 장소 + 게시물 ID · 계정 · 링크 · 게시 시각 · 처리 상태 · 좌표. 캡션 전체 · 사진은 저장하지 않음 (Developer Policies 6 "단순 표시"를 피함) |
| 삭제 요청 | `contact@suwonbible.com`, 처리방침 10번 · 서비스 안내에 표시. 받으면 해당 행사 삭제 또는 블록 차단 |
| 판매 · 광고 | 인스타에서 온 데이터를 판매하거나 맞춤 광고에 넘기지 않음 |
| 스위치 | `INSTAGRAM_SYNC_ENABLED` 기본값은 `false` 유지. 운영 서버 `.env` 에서만 `true` |

### 감수하는 위험

Meta 가 이 용도를 문제 삼으면 Platform Terms 7 에 따라 앱 정지 · 데이터 삭제 요구를 받을 수 있다. 그때는 스위치를 끄고 `scripts/instagram-data-delete.sql` 로 삭제한다.

## 켜는 순서

1. 이 결정과 화면 문구 반영 (PR 머지 → 자동 배포)
2. 옛 데이터 삭제: `scripts/instagram-data-count.sql` → 백업 → `scripts/instagram-data-delete.sql` (사용자 승인 후). 그동안 지워졌을 수 있는 게시물을 다시 보이지 않게 하기 위함
3. 운영 서버 `.env` 에 `INSTAGRAM_SYNC_ENABLED=true`, `INSTAGRAM_ACCOUNTS` 가 위 대상만인지 확인 → 앱 재시작. 토큰이 만료됐으면 먼저 재교환
4. `/api/events` 에 인스타 행사가 다시 보이는지 확인

## 다시 검토할 때

- Meta 가 연락하거나 정책 문서가 바뀌었을 때
- 개인 계정을 대상에 넣으려 할 때 (개인정보보호법 검토 먼저)
- 사업자를 등록해 비즈니스 인증이 가능해졌을 때 (앱 심사로 공식 판정 받기)
