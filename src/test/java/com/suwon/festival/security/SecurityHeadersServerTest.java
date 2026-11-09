package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 실제 내장 톰캣을 띄워 요청: 필터가 자동 등록되는지, 없는 주소(→ /error 로 넘어감)에도 헤더가 남는지
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SecurityHeadersServerTest.BoomController.class)
class SecurityHeadersServerTest {

  // 시험 전용: 처리 중 예외 → 500 오류 응답을 만들기 위한 주소 (운영 코드에는 없음)
  @TestConfiguration
  @RestController
  static class BoomController {
    @GetMapping("/test-only/boom")
    String boom() {
      throw new IllegalStateException("시험용 500");
    }
  }

  @Autowired
  private Environment env;

  private HttpResponse<Void> get(String path) throws Exception {
    return get(path, "*/*");
  }

  private HttpResponse<Void> get(String path, String accept) throws Exception {
    String port = env.getProperty("local.server.port");
    HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .header("Accept", accept).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.discarding());
  }

  private static void assertAllHeaders(HttpResponse<Void> res, String label) {
    assertThat(res.headers().firstValue("X-Content-Type-Options")).as(label).hasValue("nosniff");
    assertThat(res.headers().firstValue("X-Frame-Options")).as(label).hasValue("DENY");
    assertThat(res.headers().firstValue("Content-Security-Policy")).as(label).hasValue(SecurityHeadersFilter.CSP);
    assertThat(res.headers().firstValue("Referrer-Policy")).as(label).hasValue("strict-origin-when-cross-origin");
    assertThat(res.headers().firstValue("Permissions-Policy")).as(label).hasValueSatisfying(v -> assertThat(v).contains("camera=()"));
  }

  @Test
  void 실제_서버_응답에_보안헤더가_붙음() throws Exception {
    HttpResponse<Void> res = get("/actuator/health");

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    assertThat(res.headers().firstValue("Content-Security-Policy")).hasValue(SecurityHeadersFilter.CSP);
  }

  @Test
  void 없는_주소_오류_응답에도_보안헤더가_붙음() throws Exception {
    HttpResponse<Void> res = get("/no-such-page");

    assertThat(res.statusCode()).isEqualTo(404);
    assertThat(res.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(res.headers().firstValue("X-Frame-Options")).hasValue("DENY");
  }

  @Test
  void 오류_응답_404_500의_CSP도_정상응답과_같음() throws Exception {
    // 오류 화면(/error)으로 넘어가며 헤더가 지워지거나 다른 CSP 로 바뀌면 안 됨 — 화면(HTML) · API(JSON) 요청 모두
    for (String accept : new String[] {"text/html", "application/json"}) {
      HttpResponse<Void> notFound = get("/no-such-page", accept);
      assertThat(notFound.statusCode()).as("404 " + accept).isEqualTo(404);
      assertAllHeaders(notFound, "404 " + accept);

      HttpResponse<Void> serverError = get("/test-only/boom", accept);
      assertThat(serverError.statusCode()).as("500 " + accept).isEqualTo(500);
      assertAllHeaders(serverError, "500 " + accept);
    }
  }
}
