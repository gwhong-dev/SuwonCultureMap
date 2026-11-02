package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

// 실제 내장 톰캣을 띄워 요청: 필터가 자동 등록되는지, 없는 주소(→ /error 로 넘어감)에도 헤더가 남는지
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHeadersServerTest {

  @Autowired
  private Environment env;

  private HttpResponse<Void> get(String path) throws Exception {
    String port = env.getProperty("local.server.port");
    HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.discarding());
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
}
