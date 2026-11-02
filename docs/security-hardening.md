# 보안 강화 — 설계

> 상태: 설계 검토 중. 구현 전이며 아래 코드 동작은 대부분 PR 에서 시험으로 확인한다.
> 작성: 2026-11-02. 근거 조사는 같은 날의 조사 보고서(저장소 밖)이고, 필요한 공식 출처를 12번에 적었다.

## 1. 목표와 성공 기준

**목표**: 공개 중인 서비스의 실제 위험을 줄이고, 그 판단과 검증 과정을 보안 직무 지원 자료로 남긴다.

**성공 기준**

1. 2번 표의 "높음" 위협마다 대응과 검증 증거(테스트 · PR · 검사 결과)가 연결되어 있다.
2. 핵심 공격 3개(CSRF, 비로그인 쓰기, 익명 세션 대량 생성)가 수정 전에는 재현되거나, 이미 막혀 있으면 그 사실이 테스트로 남는다.
3. 추가 비용 0원. 로그인 3종 · 찜 · 탈퇴가 그대로 동작한다.
4. 개발자가 면접에서 CSRF 403, 익명 세션 수정, `oauth2Login` 을 택하지 않은 이유를 혼자 시연하고 설명할 수 있다.

**범위 밖**: `oauth2Login` 전환, Cloudflare 프록시, 로드밸런서 · RDS 같은 상용 구성(11번), 외부 모의 해킹, 다른 서비스 시험.

## 2. 위협 표

2026-11-02 코드와 서버를 읽기 전용으로 점검한 결과다. 위험도는 영향과 가능성을 놓고 판단한 값이며 정량 점수가 아니다.

| ID | 위협 | 현재 상태 | 위험 | 대응 |
|---|---|---|---|---|
| T1 | AWS · Cloudflare · GitHub 계정 탈취 | AWS 만 MFA 확인. 루트 계정으로 작업 | 높음 | 3번 PR 0 |
| T2 | XSS 로 세션 사용 · 화면 변조 | 이스케이프는 있으나 CSP 없음. `event.link` 를 검사 없이 `href` 에 넣음 | 높음 | PR 3 · 4 · 5 |
| T3 | CSRF 로 찜 변경 · 강제 탈퇴 | 토큰 없음, `SameSite=Lax` 에만 의존 | 중간 | PR 2 |
| T4 | 요청 폭주, 익명 세션으로 메모리 소진 | 횟수 제한 없음. `/api/auth/me` 가 세션을 만들 가능성 | 중간 | PR 1 · 6 |
| T5 | 취약한 라이브러리 · 검사 도구 오염 | 자동 검사 없음, Action 을 태그로 사용 | 중간 | PR 7 |
| T6 | 데이터 유실 | 서버 백업은 매일, PC 복사는 수동. 복원은 1회 시험 | 중간 | PR 0 |
| T7 | 권한 규칙 누락 | 검사가 컨트롤러마다 흩어짐. 새 API 는 기본 공개 | 중간 | PR 2 |
| T8 | 내부 오류 메시지 노출 | `IllegalArgumentException` 메시지를 그대로 응답. `For input string: "abc"` 확인 | 낮음 | PR 2 |
| T9 | 클릭재킹 · 다운그레이드 | 보안 헤더 없음 | 낮음 | PR 3 |
| T10 | 다른 회원 데이터 접근 | 회원 번호를 세션에서만 읽음. 코드 경로 없음 | 낮음 | PR 2 테스트로 고정 |

이미 양호하여 바꾸지 않는 것: OAuth `state` 검증, 로그인 때 세션 ID 교체, SSH 비밀번호 로그인 꺼짐, 컨테이너 비 root, IMDSv2, `.env` 권한, 앱 · DB 포트의 내부 한정, 운영에 수동 API 미등록.

## 3. 결정

| # | 결정 | 이유 | 버린 안 |
|---|---|---|---|
| 1 | **Spring Security 7.0.7 도입, 로그인은 유지** (Boot 4.0.8 관리 버전) | 권한 규칙을 한곳에 모으고 기본 거부 구조를 얻음. 로그인 코드는 이미 `state` 와 세션 교체가 있어 다시 만들 이유가 없음 | `oauth2Login`: 새 방어가 적고 계정 통합 · 저장된 ID · 개인정보 미보관 원칙을 다시 건드림. 작은 자체 필터만: 보안 직무 목적과 새 API 기본 거부에 불리 |
| 2 | **CSRF 는 `csrf.spa()`** | 정적 HTML + `fetch` 구조에 맞는 공식 방식. `SameSite` 는 보조 수단 | CSRF 끔, Origin 검사만 |
| 3 | **API 인증 실패는 401**, 페이지 이동 없음 | `fetch` 가 로그인 HTML 을 정상 응답으로 받지 않게 함 | 기본 로그인 화면 |
| 4 | **세션 기준은 `memberId` 하나** | 로그아웃 · 탈퇴 동작을 바꾸지 않음. 필터가 요청마다 인증 정보를 세우고 세션에 저장하지 않음 | `SecurityContext` 를 세션에 저장 |
| 5 | **HSTS 는 Caddy, 나머지 헤더 · CSP 는 Spring**. 헤더마다 담당은 하나 | HTTPS 는 Caddy 가 끝냄. CSP 는 앱 화면과 함께 관리 | 전부 Caddy |
| 6 | **CSP 는 Report-Only 로 시작** 후 차단 | 카카오맵에 필요한 지시어가 문서화되어 있지 않아 실제 위반을 보고 맞춰야 함 | 처음부터 차단 |
| 7 | **요청 제한은 Bucket4j + IP 별 상한 있는 캐시** | 서버 1대에 충분. Caddy 플러그인은 별도 이미지가 필요함 | Caddy 플러그인, Cloudflare |
| 8 | **Cloudflare 프록시는 지금 켜지 않음** | IP 가 이미 공개되어 교체 · 방화벽 작업이 필요하고, 국외 이전 고지 판단이 남음. 실제 공격이 생기면 재검토 | 지금 켬 |
| 9 | **기준은 ASVS 5.0.0 Level 1 + 세션 · 권한 · OAuth 의 Level 2 선택** | 민감 정보가 적은 서비스의 시작 수준. "OWASP 인증"이라고 쓰지 않음 | Level 2 전체 |
| 10 | **검사 도구: Dependabot, secret scanning, CodeQL, Trivy, ZAP baseline** | 공개 저장소에서 무료이고 겹치지 않음. 처음에는 보고만 받음 | Dependency-Check, SpotBugs (겹침) |

## 4. 구성 요소

**`SecurityConfig`** (신규, `security/`)

```java
// 설계용 예시. 컴파일 전
http.csrf((c) -> c.spa())
  .authorizeHttpRequests((a) -> a
    .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
    .requestMatchers("/api/favorites/**", "/api/members/**").authenticated()
    .requestMatchers("/api/auth/**", "/api/events/**").permitAll()
    .requestMatchers("/api/**").denyAll()
    .anyRequest().permitAll())
  .requestCache((c) -> c.requestCache(new NullRequestCache()))
  .exceptionHandling((e) -> e.authenticationEntryPoint(
      new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
  .addFilterBefore(new MemberSessionFilter(memberRepository), AnonymousAuthenticationFilter.class);
```

- `/api/**` 기본 거부로 새 API 를 깜빡해도 공개되지 않는다. 등록되지 않은 경로의 응답은 404 에서 401 로 바뀐다
- 개발 전용 수동 API(`/api/instagram/**`, `/api/events/sync`)는 `dev` · `test` 프로필에서만 허용하고 CSRF 대상에서 뺀다. 운영에는 이 예외가 등록되지 않는다

**`MemberSessionFilter`** (신규)

- `request.getSession(false)` 로 세션을 읽고 `memberId` 가 있으면 그 요청의 인증 정보를 세운다
- 세션을 새로 만들지 않는다
- **보호 경로에서만 회원 존재를 확인한다.** 탈퇴한 회원의 다른 세션이 찜을 쓰지 못하게 하고, 공개 API 에는 DB 조회를 더하지 않는다
- `@Bean` 으로 등록하면 서블릿 필터로 한 번 더 돌 수 있으므로 `new` 로 만든다

**컨트롤러와 오류 처리**

- `FavoriteController` · `MemberController` 는 필터가 보장해도 `memberId` 를 세션에서 읽는 방식을 유지한다
- 비로그인 응답은 400 에서 401 로 바뀐다. 프론트의 처리 코드를 함께 확인한다
- `GlobalExceptionHandler` 는 직접 던진 업무 오류만 메시지를 보낸다. 전용 예외 `BusinessException` 을 두고, 일반 `IllegalArgumentException` · 형 변환 실패는 고정 문구의 400 으로 응답한다

**프론트**

- 쓰기 호출 3곳(`index.html` 로그아웃 · 찜, `mypage.html` 탈퇴)이 공통 함수로 `X-XSRF-TOKEN` 을 보낸다
- `event.link` 는 `http:` · `https:` 만 허용한다. 서버 저장 시점과 화면 표시 시점 모두 검사한다
- 인라인 스크립트와 JS 로 만드는 `onclick` 을 파일과 `addEventListener` 로 옮긴다 (PR 5)

**운영 설정**

- `server.forward-headers-strategy=NATIVE` 추가. `request.isSecure()` 와 Secure CSRF 쿠키, 요청 제한의 방문자 IP 가 여기에 달려 있다
- Caddy 에 HSTS 추가

## 5. 데이터 흐름

**쓰기 요청 (찜)**

```
브라우저 fetch(POST, X-XSRF-TOKEN + 세션 쿠키)
 → Caddy(HTTPS, X-Forwarded-*) → RateLimitFilter → CsrfFilter(토큰 비교, 실패 403)
 → MemberSessionFilter(세션 memberId, 회원 존재) → 권한 규칙(없으면 401) → FavoriteController
```

**CSRF 토큰 전달**: 토큰은 늦게 만들어지므로 `/api/auth/me` 응답에서 토큰을 읽어 `XSRF-TOKEN` 쿠키를 내보낸다. 쿠키는 JS 가 읽어야 하므로 `HttpOnly` 가 아니고, 그래서 XSS 가 뚫리면 CSRF 방어도 함께 뚫린다. T2 와 묶어서 다룬다.

**쿠키와 처리방침**: 익명 방문자에게도 `XSRF-TOKEN` 쿠키가 붙는다. 개인정보가 아닌 보안용 쿠키지만 `privacy.html` 의 쿠키 설명과 맞는지 PR 2 에서 확인한다.

## 6. 작업 순서 (한 PR = 한 목적)

| PR | 내용 | 등급 | 끝났다는 증거 |
|---|---|---|---|
| 0 | **코드 외 작업(사용자)**: Cloudflare · GitHub MFA, IAM 사용자 분리, 예산 알림 10 · 15 · 20달러, Billing 의 t4g 체험 확인, PC 백업 | — | 체크 목록 |
| 1 | 익명 세션 수정(`getSession(false)`), `forward-headers-strategy` | M | 익명 요청에 `JSESSIONID` 없음 |
| 2 | Security 도입: 필터, 401, CSRF, 프론트 헤더, 오류 응답, `event.link` 검사 | L | 10번 표 + 실제 계정 로그인 |
| 3 | Caddy HSTS, 나머지 헤더, CSP Report-Only | M | 브라우저 위반 목록, Observatory |
| 4 | `innerHTML` 전수 검사와 XSS 회귀 테스트 | M | 악성 제목 · 링크가 실행되지 않음 |
| 5 | 인라인 스크립트 분리, CSP 실제 차단 | L | 지도 · 찜 · 로그인 동작 |
| 6 | 요청 횟수 제한 | M | 초과 시 429, 위조 IP 헤더로 우회 안 됨 |
| 7 | Dependabot, CodeQL, Trivy, 주 1회 ZAP baseline. Action 을 SHA 로 고정 | M | 검사 결과, 실제 경고 처리 기록 |
| 8 | ASVS CSV, 위협 표 갱신, 진단 보고서 | S | 각 항목에 증거 링크 |

PR 2 는 의존성과 설정을 **같은 PR** 에 넣는다. 의존성만 넣으면 기본 로그인 화면이 뜨고 POST 가 막힌다.

## 7. 오류 처리와 되돌리기

- **PR 2 에 문제가 생기면**: 의존성과 설정을 되돌리면 인증 데이터 변경이 없으므로 그대로 복구된다. DB 마이그레이션이 없다
- **토큰 불일치(403)**: 프론트는 토큰을 다시 받아 한 번만 재시도하고, 그래도 실패하면 안내 문구를 보인다
- **CSP 차단으로 지도가 깨지면**: Report-Only 로 되돌린다. 차단 전환은 별도 PR 이라 되돌리기 쉽다
- **요청 제한 오탐**: 학원 · 회사처럼 IP 를 공유하는 곳을 고려해 처음 수치는 넉넉히 잡고 로그로 조정한다. 로그에는 IP 를 남기지 않고 차단 횟수만 남긴다 (처리방침과 맞춤)
- **요청 제한은 로그인 시작 경로와 쓰기 API 부터** 적용한다. 지도 조회 `GET /api/events` 는 동시 접속을 보고 정한다

## 8. 테스트

기존 테스트 다수는 `webAppContextSetup(context).build()` 로 보안 필터 없이 돈다. 이 테스트는 그대로 두고 **`SecurityConfigTest` 를 새로 만들어 `.apply(springSecurity())` 를 쓴다.** `@WithMockUser` 대신 실제 `MockHttpSession` 에 `memberId` 를 넣어 필터까지 시험한다.

| 시험 | 기대 |
|---|---|
| 비로그인 찜 · 탈퇴 | 401, 로그인 페이지 이동 없음 |
| 로그인 + 토큰 없는 POST · DELETE | 403 |
| 로그인 + 토큰 있는 찜 | 200 |
| 탈퇴한 회원의 다른 세션으로 찜 | 401 |
| 회원 A 세션으로 회원 B 찜 조회 | A 의 것만 |
| 익명 `GET /api/events` · `/api/auth/me` | `JSESSIONID` 없음 |
| `/api/auth/me` 응답 | `XSRF-TOKEN` 쿠키 있음, HTTPS 프록시 뒤 Secure |
| 새 `/api/xxx` | 비로그인 거부 |
| OAuth 콜백 GET | CSRF 에 막히지 않음, `state` 검증 유지 |
| 로그아웃 후 | 세션 무효, 보호 API 401 |
| 운영 프로필 수동 API | 등록 안 됨 (`ProdSyncProtectionTest` 기대값 조정) |
| 숫자가 아닌 경로 값 | 고정 문구 400, 내부 메시지 없음 |
| `javascript:` 링크 | 저장 거부, 화면에서도 `href` 없음 |
| 악성 제목 (`node --test` 또는 브라우저) | 실행 안 됨 |

**사용자 확인**: 카카오 · 구글 · 네이버 실제 로그인, 계정 연동, 탈퇴. 앱 메모리는 도입 전후에 잰다. 공식 증가량 수치는 찾지 못했다.

## 9. 검증과 시험 범위

| 대상 | 허용 범위 |
|---|---|
| 로컬 · CI 의 내 앱 복제본 | ZAP active scan, 무차별 대입, 부하 시험 |
| 운영 | ZAP baseline 만, 주 1회 저속. 로그인 시작 경로와 외부 호스트 제외 |
| 운영에 active scan · 폭주 | 하지 않음 (AWS 정책도 폭주 금지) |
| 카카오 · 구글 · 네이버 · 재단 · Cloudflare · 타인 사이트 | 시험하지 않음 |

ZAP 대상 주소는 설정 파일에 고정해 실수로 외부를 공격하지 않게 한다. 이 표는 법률 자문이 아니며 정보통신망법 현행 조문은 PR 8 전에 직접 확인한다.

## 10. 포트폴리오 산출물

`docs/security/` 에 모은다.

- `threat-model.md`: 2번 표에 STRIDE 관점과 증거 링크를 더한 것
- `asvs-l1.csv`: 요구사항, 상태, 증거 링크, 해당 없음 이유
- `findings/`: 사건 기록. 재현 → 수정 → 회귀 테스트 → 재검증. 첫 후보는 익명 세션과 CSRF
- `decisions.md`: 3번 결정과 버린 안의 이유

취약점 상세는 **수정이 운영에 반영된 뒤** 공개한다. 공개 저장소이므로 서버 주소 · 접속 방법 · 비밀값은 어느 문서에도 쓰지 않는다. 자기 채점의 한계를 줄이려 현직자나 멘토의 검토를 받고, 받지 못했으면 그렇게 적는다.

## 11. 상용화할 때의 전환 계획 (지금은 하지 않음)

역할별 권한과 관리자 MFA, Redis 세션, 앱 2대 + ALB, RDS, WAF, Secrets Manager, 중앙 로그와 알림, 무중단 배포, 외부 모의 해킹. 비용은 서울 기준 월 약 42~80달러로 추정한다(세금 제외, 2026-11-02 단가). 재단 데이터 · 인스타 · 포스터 저작권의 상업 이용 조건을 먼저 확인해야 한다.

## 12. 미확인 항목과 출처

**구현에서 확인할 것**: `/api/auth/me` 가 실제로 익명 세션을 만드는지, 토큰 쿠키 전달 방식, 필터 이중 실행, Tomcat 의 내부 프록시 인식(Docker 네트워크), Secure 쿠키, `Cache-Control` 이 정적 파일 캐시에 미치는 영향, 카카오 SDK 의 CSP 요구 사항, 메모리 증가량.

**공식 출처**: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) · [7.0 변경 사항](https://docs.spring.io/spring-security/reference/7.0/whats-new.html) · [ASVS 5.0.0](https://github.com/OWASP/ASVS/tree/v5.0.0) · [OWASP CSRF](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html) · [AWS 침투 테스트 정책](https://aws.amazon.com/security/penetration-testing/) · [ZAP baseline](https://www.zaproxy.org/docs/docker/baseline-scan/) · [Trivy 권고](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23)
