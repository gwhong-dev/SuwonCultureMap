# 캡션 AI 공급자 — Gemini 무료 등급 (2026-11-24)

## 결정

- 규칙 파서가 판단 못 한 인스타 캡션 블록(`PENDING`)을 **Google Gemini API 무료 등급**(`gemini-3.5-flash-lite`)으로 정리한다 (사용자 결정)
- 설계 문서 [instagram-integration.md](../instagram-integration.md) 8번 "AI 공급자" 열린 결정을 닫음

## 고른 이유 · 버린 안

| 안 | 결과 | 이유 |
|---|---|---|
| **Gemini 무료 등급** | ✅ 채택 | 비용 0, 카드 등록 없음. 무료 · 유료가 같은 키 · 같은 코드라 결제만 연결하면 유료 조건으로 바뀜 (코드 변경 없음) |
| Claude Haiku 5.5 | 버림 | 한 달 수십 원 수준이지만 선불 충전 필요. 무료 대비 이점(학습 미사용)은 결제 연결한 Gemini 로도 얻을 수 있음 |
| Ollama (내 PC) | 버림 | 운영 서버(EC2 2GB)에서 돌릴 수 없음 |
| `gemini-2.5-flash` | 버림 | 2026-12-01 종료 예정 (공식 문서) |

## 감수한 위험 (사용자가 알고 고름)

독립 검증 에이전트가 원문을 읽고 판정 (법률 자문이 아닌 약관 문구 검토):

| 항목 | 판정 | 근거 |
|---|---|---|
| 인스타 캡션 · 거기서 뽑은 값 | Meta "Platform Data" 에 해당 | Meta Platform Terms 12.l (파생 데이터 포함) |
| 외부 업체(Gemini)에 보낼 조건 | 업체가 자기 목적으로 쓰지 않는다는 서면 동의 필요 | Meta Platform Terms 5.a.i.1 |
| Gemini 무료 등급 | 입력을 Google 제품 개선 · ML 개발에 사용 | Gemini API 추가 약관 "Unpaid Services" |
| 문구상 충돌 | **충돌** | 위 두 조항 |
| 비상업 · 공익 예외 | **없음** | Meta Terms 전체 · 3.e 예외는 Messenger · 리드 · Shops 데이터만 |
| 현실적 위험 | **낮음** — 발견돼도 결과는 앱 제한 · 토큰 회수 → 인스타 수집 중단. 재단 API 행사는 그대로 | Meta Terms 7.e |

## 위험을 줄이는 장치 (이번 PR)

1. **끄는 스위치**: `CAPTION_AI_ENABLED=false` 면 키가 있어도 AI 를 쓰지 않음 (`CaptionAiConfig`)
2. **보내기 전 지우기**: 링크 · @계정 · 메일 · 전화번호 (`GeminiCaptionClient.scrub`)
3. **원문 비저장**: AI 결과 보관(V11 `caption_ai_result`)은 원문 해시 + 뽑은 값만
4. **개인정보처리방침**: 인스타 행 안에 "읽지 못한 캡션 일부를 Gemini 로 보냄 · 무료 등급이라 Google 이 서비스 개선에 쓸 수 있음" 추가 (Meta Terms 4조)

## 유료로 바꿀 때 (결제 계정만 연결, 코드 그대로)

먼저 오는 때: Meta 앱 검수 · 고급 액세스 신청 / Meta 가 데이터 사용 확인 · 업체 목록 요청 (Terms 5.a.iv) / 서비스를 공개 홍보할 때

## 구조

```
규칙 파서 ─ 판단 못 함 → CachedCaptionAiClient ─ 보관 있음 → 그대로 (AI 안 부름)
                              └ 없음 → 하루 상한(200) → GeminiCaptionClient → 결과 보관
                                                         └ 실패(429 · 5xx · 연결) → 예외 → PENDING (보관 안 함, 다음에 다시)
결과 → ParsedEventValidator 다시 검사 (날짜 · 장소가 블록 원문에 있어야 통과)
```

- AI 가 낸 "행사 아님"은 이유 `AI: 행사 아님` 으로 구분 → 같은 글의 옛 행사를 내리는 "확정" 근거로 쓰지 않음 (`StaleBlockRetirer`)
- 한계: 검사기는 값이 원문에 **있는지**만 봄 → 원문에 글자 그대로 있는 엉뚱한 날짜는 못 거름. 수집 대상을 공식 계정으로 한정해 둔 것으로 대신함
- API 형태: 2026-11-24 공식 문서 기준 Interactions API (`v1beta/interactions`, snake_case). 실제 키로 1회 호출해 확인 (PR 댓글)
