// 브라우저 화면 시험 (Playwright) 설정 — 실행 방법 · 구조는 docs/e2e-test.md
//  - 앱을 test 프로필(H2 메모리 DB · 가짜 키)로 18080 포트에 띄우고, 시험이 끝나면 Playwright 가 끔
//  - 카카오 SDK · 행사 API 는 시험 안에서 가짜로 바꿔 네트워크에 기대지 않음 (src/test/e2e/support.js)
const path = require("path");
const { defineConfig, devices } = require("@playwright/test");

const PORT = Number(process.env.E2E_PORT || 18080);
// Windows 는 셸이 현재 폴더 실행 파일을 못 찾을 수 있어 절대 경로로
const mvnw = process.platform === "win32" ? '"' + path.join(__dirname, "mvnw.cmd") + '"' : "./mvnw";

module.exports = defineConfig({
  testDir: "src/test/e2e",
  testMatch: "*.spec.js",
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  workers: 1, // 앱 하나를 같이 씀
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:" + PORT,
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
    reducedMotion: "reduce", // 첫 진입 애니메이션 · 부드러운 스크롤 끔 (intro.js 는 움직임 줄이기면 재생 안 함)
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    // additional-location: src/test/resources 의 application-test.properties 를 읽게 함 (spring-boot:run 은 main 경로만 봄)
    // devtools 재시작은 끔 (시험 중 파일이 바뀌어도 앱이 다시 뜨지 않게)
    command:
      mvnw +
      " -q spring-boot:run -Dspring-boot.run.profiles=test" +
      ' "-Dspring-boot.run.arguments=--server.port=' + PORT +
      " --app.base-url=http://localhost:" + PORT +
      " --spring.config.additional-location=optional:file:./src/test/resources/" +
      ' --spring.devtools.restart.enabled=false"',
    url: "http://localhost:" + PORT + "/actuator/health",
    timeout: 240_000, // 첫 실행은 컴파일 포함
    reuseExistingServer: false, // 다른 프로필로 떠 있는 앱을 잘못 쓰지 않게 늘 새로 띄움
    // 앱 로그는 시험 출력이 묻히지 않게 기본은 숨김. 앱이 안 뜨면 E2E_SERVER_LOG=1 로 다시 돌려 시작 오류를 봄 (#267 검증 ⚠️)
    stdout: process.env.E2E_SERVER_LOG ? "pipe" : "ignore",
    stderr: "pipe",
  },
});
