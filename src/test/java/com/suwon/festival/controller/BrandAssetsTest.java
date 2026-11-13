package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 서비스 브랜드(11/11): 이름 수원바이블 + 부제 수원 문화지도 · 아이콘 · manifest · 공유 이미지 · Pretendard 글꼴
@SpringBootTest
@ActiveProfiles("test")
class BrandAssetsTest {

  private static final String[] PAGES = { "/index.html", "/about.html", "/login.html", "/mypage.html", "/privacy.html" };

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 모든_화면의_탭_제목에_이름과_부제가_함께_있고_아이콘_글꼴을_연결함() throws Exception {
    for (String page : PAGES) {
      String html = body(page);
      assertThat(html).as(page)
          .containsPattern("<title[^>]*>[^<]*수원바이블 · 수원 문화지도</title>") // 화면 언어 표시(data-i18n)가 붙을 수 있음
          .contains("<meta name=\"theme-color\" content=\"#b4472a\" />")
          .contains("href=\"/brand/favicon.svg\"", "href=\"/brand/apple-touch-icon.png\"", "href=\"/brand/manifest.json\"")
          .contains("href=\"/css/fonts.css\"")
          .doesNotContain("fonts.googleapis.com", "fonts.gstatic.com");
    }
  }

  @Test
  void 첫_화면에_공유_미리보기_메타와_로고가_있음() throws Exception {
    String html = body("/index.html");
    assertThat(html)
        .contains("property=\"og:title\" content=\"수원바이블 · 수원 문화지도\"")
        .contains("property=\"og:image\" content=\"https://suwonbible.com/brand/og-image.png\"")
        .contains("class=\"topbar-brand\"", "class=\"home-logo\"")
        // 로고 = 도장 + 워드마크 + 아래 줄 부제 (글자 윤곽 SVG, 11/11 낙관 + 핀)
        .contains("href=\"/brand/logo.svg#seal-sm\"", "href=\"/brand/logo.svg#wordmark\"", "href=\"/brand/logo.svg#tagline\"")
        .contains("aria-label=\"수원바이블 · 수원 문화지도\"");
  }

  @Test
  void 아이콘_공유이미지_글꼴_파일이_열림() throws Exception {
    String[][] files = {
        { "/favicon.ico", "image/" }, // 브라우저가 따로 묻는 기본 주소
        { "/brand/favicon.svg", "image/svg+xml" },
        { "/brand/favicon-32.png", "image/png" },
        { "/brand/apple-touch-icon.png", "image/png" },
        { "/brand/icon-192.png", "image/png" },
        { "/brand/icon-512.png", "image/png" },
        { "/brand/og-image.png", "image/png" },
        { "/brand/logo-mark.svg", "image/svg+xml" },
        { "/brand/logo.svg", "image/svg+xml" },
        { "/brand/logo-lockup.svg", "image/svg+xml" },
        { "/brand/fonts/Pretendard-Regular.subset.woff2", "font/woff2" },
        { "/brand/fonts/Pretendard-Medium.subset.woff2", "font/woff2" },
        { "/brand/fonts/Pretendard-Bold.subset.woff2", "font/woff2" },
        { "/brand/fonts/OFL-LICENSE.txt", "text/plain" }, // 글꼴 라이선스(OFL)는 글꼴과 함께 배포
    };
    for (String[] f : files) {
      MockHttpServletResponse res = mockMvc.perform(get(f[0])).andReturn().getResponse();
      assertThat(res.getStatus()).as(f[0]).isEqualTo(200);
      assertThat(res.getContentType()).as(f[0]).startsWith(f[1]);
      assertThat(res.getContentAsByteArray().length).as(f[0]).isPositive();
    }
  }

  @Test
  void 로고_SVG는_글꼴_없이_윤곽으로_그리고_글꼴_라이선스를_적음() throws Exception {
    String sprite = body("/brand/logo.svg");
    assertThat(sprite)
        .contains("<symbol id=\"seal\"", "<symbol id=\"seal-sm\"", "<symbol id=\"wordmark\"", "<symbol id=\"tagline\"")
        .contains("#b4472a") // 도장 벽돌색
        .contains("SIL Open Font License")
        .doesNotContain("<text", "font-family", "@font-face"); // 글자는 path 로만
    for (String f : new String[] { "/brand/logo-mark.svg", "/brand/logo-lockup.svg", "/brand/favicon.svg" }) {
      assertThat(body(f)).as(f).contains("<path", "SIL Open Font License").doesNotContain("<text", "font-family");
    }
  }

  @Test
  void manifest는_이름_벽돌색_아이콘_192_512를_담음() throws Exception {
    JsonNode m = new ObjectMapper().readTree(body("/brand/manifest.json"));
    assertThat(m.path("name").asText()).isEqualTo("수원바이블 · 수원 문화지도");
    assertThat(m.path("short_name").asText()).isEqualTo("수원바이블");
    assertThat(m.path("theme_color").asText()).isEqualTo("#b4472a");
    assertThat(m.path("icons").findValuesAsText("sizes")).containsExactly("192x192", "512x512");
  }

  private String body(String path) throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get(path)).andReturn().getResponse();
    assertThat(res.getStatus()).as(path).isEqualTo(200);
    return res.getContentAsString(StandardCharsets.UTF_8);
  }
}
