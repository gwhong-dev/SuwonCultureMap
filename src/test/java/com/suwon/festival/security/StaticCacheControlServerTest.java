package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

// 정적 파일(HTML · JS · CSS)은 Cache-Control: no-cache → 브라우저가 쓰기 전에 매번 서버에 확인
//   (설정이 없으면 Last-Modified 로 캐시 시간을 추측해, 배포 직후 새 HTML + 옛 JS 가 섞일 수 있음)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StaticCacheControlServerTest {

  @Autowired
  private Environment env;

  private HttpResponse<Void> get(String path, String ifModifiedSince) throws Exception {
    String port = env.getProperty("local.server.port");
    HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    if (ifModifiedSince != null) {
      request.header("If-Modified-Since", ifModifiedSince);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "/index.html", "/js/index.js", "/css/index.css", "/schedule-format.js"})
  void 정적_파일은_매번_서버에_확인하고_보안헤더도_유지(String path) throws Exception {
    HttpResponse<Void> res = get(path, null);

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.headers().firstValue("Cache-Control")).hasValue("no-cache");
    assertThat(res.headers().firstValue("Content-Security-Policy")).hasValue(SecurityHeadersFilter.CSP);
    assertThat(res.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/index.html", "/js/index.js"})
  void 바뀌지_않은_파일은_304_로_본문_없이_응답(String path) throws Exception {
    String lastModified = get(path, null).headers().firstValue("Last-Modified").orElseThrow();

    HttpResponse<Void> res = get(path, lastModified);

    assertThat(res.statusCode()).isEqualTo(304);
    assertThat(res.headers().firstValue("Cache-Control")).hasValue("no-cache");
  }
}
