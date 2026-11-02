# 보안 강화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인을 유지한 채 Spring Security, 보안 헤더 · CSP, 요청 제한, 자동 검사를 넣고 검증 기록을 남긴다.

**Architecture:** 세션의 `memberId` 를 읽는 필터 위에 Spring Security 7.0.7 권한 규칙과 `csrf.spa()` 를 둔다. HSTS 는 Caddy, 나머지 헤더는 Spring 이 맡는다. 태스크 하나가 PR 하나다 (Task 0 은 사용자 작업).

**Tech Stack:** Spring Boot 4.0.8, Spring Security 7.0.7, Bucket4j 8.21.0, Caddy, GitHub Actions, MockMvc + `spring-security-test`, `node --test`

**Spec:** `docs/security-hardening.md`

## Global Constraints

- 기능 브랜치 → PR → (M · L) 검증 에이전트 → CI → 사용자 승인 → 머지. `main` 직접 커밋 금지
- 추가 비용 0원. 이메일 · 이름 · IP 를 새로 저장하거나 로그에 남기지 않음
- `application-dev.properties` · `.env` 는 실행에만 쓰고 출력 · 커밋하지 않음
- 공개 문서에 서버 주소 · 접속 방법 · 비밀값을 쓰지 않음. 취약점 상세는 운영 반영 뒤 공개
- 공격성 시험은 로컬 · CI 복제본에서만. 운영에는 ZAP baseline 저속만. 소셜 제공자 · 재단 · Cloudflare · 타인 사이트 시험 금지
- `oauth2Login`, Cloudflare 프록시, DB 스키마 변경은 하지 않음
- 테스트는 `test` 프로필. 전체 실행은 `.\mvnw.cmd test` (Windows)

## Review Focus

- 로그인 없이 찜을 누름 → 401 본문이 비어도 빈 경고창이 아니라 로그인 안내가 나옴 (Task 2)
- 쿠키를 지웠거나 오래 열어 둔 탭의 쓰기 요청 → 403 뒤 토큰을 받아 1회 재시도, 그래도 실패하면 안내 (Task 2)
- 소셜 동의 화면에서 돌아오는 콜백 GET → CSRF · 권한 규칙에 막히지 않고, 계정 연동도 유지 (Task 2)
- `X-Forwarded-For` 를 방문자가 위조 → 제한을 우회하지 못하고, 한 IP 초과가 다른 IP 를 막지 않음 (Task 6)
- CSP 전환 뒤 카카오맵 · 마커 · 인포윈도우 · 찜 버튼 → 모두 동작 (Task 5)

---

### Task 0: 계정 · 운영 점검 (사용자 작업, PR 없음)

- [ ] Cloudflare · GitHub MFA 확인, AWS 일상 작업용 IAM 사용자 + MFA (AWS Organizations 는 가입하지 않음)
- [ ] 예산 알림 10 · 15 · 20달러, 메일 인증 `1/1`, Billing 에서 t4g 체험 적용 확인
- [ ] 서버 백업을 PC 로 복사

### Task 1: 익명 세션 제거 · 프록시 인식 (M)

**Files:**
- Modify: `controller/AuthController.java` (`me`), `src/main/resources/application-prod.properties`, `docker-compose` 필요 시
- Test: `src/test/java/com/suwon/festival/controller/AnonymousSessionTest.java`

**Interfaces:**
- Produces: `GET /api/auth/me` 는 세션을 만들지 않음. `request.isSecure()` 가 프록시 뒤에서 true

- [ ] **Step 1: 실패 테스트.** `익명_me_는_세션을_만들지_않음`: `GET /api/auth/me` 후 `result.getRequest().getSession(false)` 가 null, `JSESSIONID` 쿠키 없음, 본문 `loggedIn=false`. `로그인_세션은_그대로_인식`: `MockHttpSession` 의 `memberId` 로 `loggedIn=true`
- [ ] **Step 2: 실행.** `.\mvnw.cmd test -Dtest=AnonymousSessionTest` → 세션 생성이 실제면 FAIL. 통과하면 "이미 안전"을 기록하고 Step 3 은 그래도 적용 (`HttpSession` 매개변수 제거가 의도를 드러냄)
- [ ] **Step 3: 구현.** `me(HttpServletRequest request)`, `request.getSession(false)`. 다른 콜백의 세션 생성은 유지
- [ ] **Step 4: 프록시 설정.** 운영 설정에 `server.forward-headers-strategy=NATIVE`. 테스트: `X-Forwarded-Proto: https` 요청의 `isSecure()` 시험은 Tomcat 밸브라 MockMvc 로 확인되지 않으므로, 임시 compose 또는 배포 뒤 Set-Cookie 의 `Secure` 로 확인하고 PR 에 결과를 적음
- [ ] **Step 5: 전체 테스트 후 커밋.** `fix: 익명 요청이 세션을 만들지 않게 수정` 

### Task 2: Spring Security 도입 (L)

**Files:**
- Modify: `pom.xml` (`spring-boot-starter-security`, 테스트 `spring-security-test`), `controller/GlobalExceptionHandler.java`, `FavoriteService.java` · `AuthService.java` · 컨트롤러의 업무 오류, `service/EventSyncService.java`, `static/index.html`, `static/mypage.html`, `static/privacy.html`(필요 시), `controller/ProdSyncProtectionTest.java`
- Create: `security/SecurityConfig.java`, `security/MemberSessionFilter.java`, `BusinessException.java`, `static/api-client.js`
- Test: `controller/SecurityConfigTest.java`, `src/test/js/api-client.test.js`

**Interfaces:**
- Consumes: Task 1 의 세션 비생성
- Produces: `SecurityFilterChain securityFilterChain(HttpSecurity)`; `MemberSessionFilter(MemberRepository)`; `BusinessException(String)`; JS `apiFetch(url, options) → Promise<Response>` (쓰기 호출 공통, `X-XSRF-TOKEN` 첨부, 403 시 `/api/auth/me` 후 1회 재시도); `safeHttpUrl(value) → string|null`

- [ ] **Step 1: 실패 테스트.** `.apply(springSecurity())` 로 `docs/security-hardening.md` 8번 표의 항목을 각각 작성. 이름 예: `비로그인_찜은_401`, `토큰_없는_POST는_403`, `토큰_있는_찜은_200`, `탈퇴한_회원_세션은_401`, `회원_A는_A의_찜만_봄`, `미등록_API는_거부`, `콜백_GET은_CSRF에_막히지_않음`, `me_응답에_XSRF_쿠키`, `로그아웃_뒤_보호_API는_401`, `숫자_아닌_경로값은_고정_문구_400`. 인증은 `MockHttpSession` 의 `memberId`, 토큰은 `csrf().asHeader()`
- [ ] **Step 2: 실행.** `.\mvnw.cmd test -Dtest=SecurityConfigTest` → 의존성 추가 전 컴파일 실패 확인
- [ ] **Step 3: 의존성과 `SecurityConfig` · `MemberSessionFilter` 를 같은 커밋에.** 규칙은 설계 4번 그대로. `csrf.spa()`, `HttpStatusEntryPoint(UNAUTHORIZED)`, `NullRequestCache`, 필터는 `new` 로 생성해 `AnonymousAuthenticationFilter` 앞에. 필터는 보호 경로에서만 `existsById`. `dev` · `test` 프로필에서만 `/api/instagram/**` · `/api/events/sync` 허용과 CSRF 예외. HSTS 는 Spring 에서 끔
- [ ] **Step 4: 쿠키 전달.** `/api/auth/me` 에서 `CsrfToken.getToken()` 을 읽어 쿠키를 내보냄. `Secure` 는 배포 전 임시 compose 또는 배포 뒤 확인
- [ ] **Step 5: 오류 응답.** 업무 오류를 `BusinessException` 으로 바꾸고 핸들러는 그 메시지만 보냄. `IllegalArgumentException` · `MethodArgumentTypeMismatchException` 은 400 `"요청 값이 올바르지 않습니다."`
- [ ] **Step 6: 링크 검사.** 저장 시 `http` · `https` 아닌 `link` 는 null 로 저장 (`EventSyncService`, 인스타 저장 경로 포함). 테스트 `javascript_링크는_저장_안_함`. 화면은 `safeHttpUrl` 통과 때만 `href`
- [ ] **Step 7: 프론트.** `api-client.js` 와 JS 시험 (토큰 첨부, 403 재시도 1회, `safeHttpUrl`). 쓰기 호출 3곳 교체. 찜의 401 은 고정 안내 문구
- [ ] **Step 8: 기존 테스트 조정.** `ProdSyncProtectionTest` 등이 보안 필터 없이 도는 것을 유지하되 운영 프로필 + `springSecurity()` 시험 1개 추가. CI 의 `node --test` 에 새 파일 추가
- [ ] **Step 9: 전체 시험.** `.\mvnw.cmd test`, `node --test src/test/js/schedule-format.test.js src/test/js/api-client.test.js` → 전부 통과
- [ ] **Step 10: 실제 실행.** 임시 DB 로 앱 실행, 도입 전후 메모리 측정, 쿠키 안내와 `privacy.html` 대조. 사용자가 카카오 · 구글 · 네이버 로그인 · 연동 · 찜 · 탈퇴 확인
- [ ] **Step 11: 커밋 + PR.** `feat: Spring Security 도입 (세션 인증 필터, CSRF, 401)` 등 의미 단위

### Task 3: 헤더 · CSP Report-Only (M)

**Files:** Modify `deploy/Caddyfile`, `security/SecurityConfig.java`. Test: `SecurityConfigTest`

**Interfaces:** Consumes Task 2 `SecurityFilterChain`. Produces 상수 `SecurityConfig.CSP_POLICY`

- [ ] **Step 1: 실패 테스트.** `보안_헤더가_붙음`: `Content-Security-Policy-Report-Only`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `X-Frame-Options: DENY`. `정적_파일_캐시가_막히지_않음`: `/schedule-format.js` 에 `no-store` 없음
- [ ] **Step 2: 구현.** 시작 정책은 `*.kakaocdn.net`, `dapi.kakao.com`, 행사 이미지 출처, `'self'`. 지시어는 브라우저 위반으로 조정. 정적 파일은 `cacheControl` 예외. Caddy 에 `Strict-Transport-Security "max-age=31536000"` (preload 없음)
- [ ] **Step 3: 확인.** 로컬에서 지도 · 찜 · 상세를 돌며 위반 목록 수집, 배포 뒤 Mozilla Observatory 점수를 기록 (앱 안전의 증거로 쓰지 않음)
- [ ] **Step 4: 커밋.** `feat: 보안 헤더와 CSP Report-Only 추가`

### Task 4: XSS 회귀 시험 (M)

**Files:** Modify `static/index.html`, `static/mypage.html`. Create `static/render-safety.js`(필요 시). Test: `src/test/js/render-safety.test.js`

- [ ] **Step 1:** `innerHTML` 8곳을 표로 정리: 데이터 출처, 이스케이프 여부. 결과를 PR 본문에
- [ ] **Step 2: 실패 테스트.** `<img src=x onerror=...>` 제목 · 장소 · 카테고리, `javascript:` 링크, 작은따옴표가 든 값(`onclick` 문자열)이 태그나 실행으로 바뀌지 않음
- [ ] **Step 3: 구현.** 빠진 곳에 `escapeHtml`. 속성값 안 문자열은 데이터 속성으로 옮김
- [ ] **Step 4:** 임시 DB 에 악성 값을 넣어 브라우저 확인. 사건 기록 `docs/security/findings/` 작성
- [ ] **Step 5: 커밋.** `fix: 화면 출력 이스케이프 보강과 회귀 시험`

### Task 5: 인라인 스크립트 분리 · CSP 차단 (L)

**Files:** Create `static/app.js`, `static/mypage.js`, `static/login.js`(인라인이 있으면). Modify 각 HTML, `SecurityConfig.java`, `ScheduleDisplayPageTest.java`. Test: `SecurityConfigTest`

**Interfaces:** Consumes Task 3 `CSP_POLICY`, Task 2 `apiFetch`

- [ ] **Step 1: 실패 테스트.** 헤더 이름이 `Content-Security-Policy`, `script-src` 에 `unsafe-inline` 없음. HTML 에 인라인 `<script>` · `onclick=` 없음
- [ ] **Step 2: 구현.** 스크립트를 파일로 옮기고 `onclick` 을 이벤트 위임으로. 인라인 `style` 은 `style-src` 를 위반 목록을 보고 결정, 허용했다면 이유를 `decisions.md` 에 기록
- [ ] **Step 3: 확인.** 전체 시험 후 브라우저에서 위반 0, 지도 · 마커 · 인포윈도우 · 찜 · 로그인 · 캘린더 동작, 모바일 크기
- [ ] **Step 4: 커밋.** `refactor: 인라인 스크립트 분리`, `feat: CSP 차단 전환`

### Task 6: 요청 횟수 제한 (M)

**Files:** Modify `pom.xml` (`bucket4j_jdk17-core` 8.21.0), `SecurityConfig.java`. Create `security/RateLimitFilter.java`. Test: `security/RateLimitFilterTest.java`

**Interfaces:** Consumes Task 1 프록시 설정. Produces `RateLimitFilter(RateLimitProperties)`, 설정 `app.rate-limit.*`

- [ ] **Step 1: 실패 테스트.** `로그인_시작_한도_초과는_429`(분당 30회), `쓰기_API_한도_초과는_429`(분당 60회), `다른_IP는_영향_없음`, `위조_XFF로_우회_안_됨`, `IP_캐시에_상한`(최대 10,000개). 수치는 근거 없는 시작값이라 설정으로 뺌
- [ ] **Step 2: 구현.** `getRemoteAddr()` 기준, 만료 있는 상한 캐시. `GET /api/events` 는 제외. 로그에는 경로별 차단 횟수만, IP 금지
- [ ] **Step 3: 시험.** 실제 프록시 뒤 방문자 IP 인식을 임시 compose 또는 배포 뒤 확인. Docker 내부 대역이 Tomcat 기본 내부 프록시에 해당하는지 확인, 안 되면 `server.tomcat.remoteip.internal-proxies` 설정
- [ ] **Step 4: 커밋.** `feat: 로그인 · 쓰기 요청 횟수 제한`

### Task 7: 자동 보안 검사 (M)

**Files:** Create `.github/dependabot.yml`, `.github/workflows/zap-baseline.yml`, `.zap/rules.tsv`. Modify `.github/workflows/ci.yml`

- [ ] **Step 1:** 저장소 설정에서 사용자가 secret scanning, push protection, CodeQL 기본 설정을 켬
- [ ] **Step 2:** Dependabot: `maven` · `github-actions` · `docker` 주 1회
- [ ] **Step 3:** CI 의 외부 Action 을 전체 SHA 로 고정, `permissions` 최소화. Trivy 이미지 검사 추가. 처음에는 보고만, 비밀값 전달 없음. 고정할 SHA 는 작업 시점 공식 릴리스에서 확인 (GHSA-69fq-xp46-6x23 영향 버전 제외)
- [ ] **Step 4:** ZAP baseline 은 주 1회 + 수동 실행. 대상 `https://suwonbible.com` 고정, `OUTOFSCOPE` 로 `/api/auth/.*/login` 과 외부 호스트 제외, 저속
- [ ] **Step 5:** 첫 결과 처리 기록을 남기고 커밋. `chore: 의존성 · 이미지 · 웹 자동 검사 추가`

### Task 8: 검증 기록 (S)

**Files:** Create `docs/security/threat-model.md`, `asvs-l1.csv`, `decisions.md`, `findings/*.md`. Modify `README.md`, `PROGRESS.md`

- [ ] **Step 1:** 공식 CSV 에서 Level 1 전체 70개 + V7 · V8 · V10 Level 2 선택을 옮기고 상태, 증거, 해당 없음 이유 기재. 검증 안 된 항목은 미확인으로
- [ ] **Step 2:** 로컬 복제본에 ZAP full scan, 결과를 사건 기록으로. 대상 `localhost` 고정
- [ ] **Step 3:** 정보통신망법 현행 조문 확인, 시험 범위 문서에 반영. "OWASP 인증" 문구 금지 확인, 서버 주소 · 비밀값 검색
- [ ] **Step 4:** 개발자 시연 연습: CSRF 403, 익명 세션, `oauth2Login` 미선택 이유
- [ ] **Step 5: 커밋.** `docs: 위협 모델 · ASVS 점검표 · 사건 기록`
