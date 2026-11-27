package com.suwon.festival.controller;

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

// 검색엔진(네이버 서치어드바이저 · 구글 서치콘솔)에 낼 robots.txt · sitemap.xml 이 실제 서버에서 200 으로 나오는지
//   행사별 ?event= 주소는 같은 HTML 을 돌려줘 중복 문서가 되므로 사이트맵에 넣지 않음 (행사별 페이지 작업 때 추가)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SearchCrawlerFilesTest {

  @Autowired
  private Environment env;

  private HttpResponse<String> get(String path) throws Exception {
    String port = env.getProperty("local.server.port");
    HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void robots_txt_는_text_plain_으로_열리고_로그인_마이페이지_API_를_막고_사이트맵을_알림() throws Exception {
    HttpResponse<String> res = get("/robots.txt");

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/plain"));
    assertThat(res.body())
        .contains("User-agent: *", "Allow: /", "Disallow: /api/", "Disallow: /login.html", "Disallow: /mypage.html")
        .contains("Sitemap: https://suwonbible.com/sitemap.xml");
  }

  @Test
  void sitemap_xml_은_xml_로_열리고_공개_화면_세_개만_담음() throws Exception {
    HttpResponse<String> res = get("/sitemap.xml");

    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).contains("xml"));
    assertThat(res.body())
        .contains("http://www.sitemaps.org/schemas/sitemap/0.9")
        .contains("<loc>https://suwonbible.com/</loc>", "<loc>https://suwonbible.com/about.html</loc>",
            "<loc>https://suwonbible.com/privacy.html</loc>")
        .doesNotContain("?event=", "login.html", "mypage.html");
    assertThat(res.body().split("<loc>", -1)).hasSize(4); // <loc> 세 개
  }
}
