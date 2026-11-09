package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// 화면이 불러오는 외부 CSS(<link rel=stylesheet> · CSS @import) 주소가 CSP style-src 에 들어 있는지 (#113 검증 ⚠️)
// 외부 CSS 를 새로 넣고 style-src 를 안 고치면 운영에서만 조용히 막힘 → 시험에서 먼저 잡음
class CspStyleSourceTest {

  private static final Path STATIC_DIR = Path.of("src/main/resources/static");

  // <link ...> 태그 하나 (여러 줄로 나눠 써도 잡음)
  private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ATTR = Pattern.compile("([\\w-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);
  // @import url("...") · @import '...'
  private static final Pattern CSS_IMPORT = Pattern.compile(
      "@import\\s+(?:url\\(\\s*)?[\"']?([^\"')\\s;]+)", Pattern.CASE_INSENSITIVE);

  @Test
  void HTML의_외부_stylesheet_주소는_style_src에_있음() throws IOException {
    List<String> blocked = new ArrayList<>();
    int external = 0;
    for (Path file : files(".html")) {
      String html = Files.readString(file, StandardCharsets.UTF_8);
      for (String href : stylesheetHrefs(html)) {
        if (isExternal(href)) {
          external++;
          if (!allowedByStyleSrc(href)) {
            blocked.add(STATIC_DIR.relativize(file) + " " + href);
          }
        }
      }
    }
    // 지금은 Google Fonts 1개. 0이면 검사식이 아무것도 못 찾는 것
    assertThat(external).as("외부 stylesheet 를 하나도 못 찾음 (검사식 확인)").isPositive();
    assertThat(blocked).as("SecurityHeadersFilter.CSP 의 style-src 에 도메인 추가 필요").isEmpty();
  }

  @Test
  void CSS의_외부_import_주소는_style_src에_있음() throws IOException {
    List<String> blocked = new ArrayList<>();
    for (Path file : files(".css")) {
      String css = Files.readString(file, StandardCharsets.UTF_8);
      Matcher m = CSS_IMPORT.matcher(css);
      while (m.find()) {
        String url = m.group(1);
        if (isExternal(url) && !allowedByStyleSrc(url)) {
          blocked.add(STATIC_DIR.relativize(file) + " " + url);
        }
      }
    }
    assertThat(blocked).as("SecurityHeadersFilter.CSP 의 style-src 에 도메인 추가 필요").isEmpty();
  }

  @Test
  void 검사식이_실제로_잡아냄() {
    // 여러 줄 link · 속성 순서 · preload as=style 도 stylesheet 로 봄, 아이콘 · preconnect 는 제외
    assertThat(stylesheetHrefs("<link\n  rel=\"stylesheet\"\n  media=\"x\"\n  href=\"https://a.example/x.css\"\n/>"))
        .containsExactly("https://a.example/x.css");
    assertThat(stylesheetHrefs("<link href='//b.example/y.css' rel='stylesheet'>")).containsExactly("//b.example/y.css");
    assertThat(stylesheetHrefs("<link rel=\"preload\" as=\"style\" href=\"https://c.example/z.css\">"))
        .containsExactly("https://c.example/z.css");
    assertThat(stylesheetHrefs("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />")).isEmpty();
    assertThat(stylesheetHrefs("<link rel=\"icon\" href=\"https://d.example/i.png\" />")).isEmpty();

    Matcher m = CSS_IMPORT.matcher("@import url(\"https://e.example/a.css\"); @import 'https://f.example/b.css';");
    assertThat(m.find()).isTrue();
    assertThat(m.group(1)).isEqualTo("https://e.example/a.css");
    assertThat(m.find()).isTrue();
    assertThat(m.group(1)).isEqualTo("https://f.example/b.css");

    assertThat(isExternal("/css/index.css")).isFalse();
    assertThat(allowedByStyleSrc("https://fonts.googleapis.com/css2?family=Noto+Sans+KR")).isTrue();
    assertThat(allowedByStyleSrc("https://cdn.example.com/x.css")).isFalse();
  }

  private static List<String> stylesheetHrefs(String html) {
    List<String> hrefs = new ArrayList<>();
    Matcher tag = LINK_TAG.matcher(html);
    while (tag.find()) {
      String rel = "";
      String as = "";
      String href = null;
      Matcher a = ATTR.matcher(tag.group());
      while (a.find()) {
        switch (a.group(1).toLowerCase()) {
          case "rel" -> rel = a.group(3).toLowerCase();
          case "as" -> as = a.group(3).toLowerCase();
          case "href" -> href = a.group(3).trim();
          default -> { }
        }
      }
      List<String> rels = Arrays.asList(rel.split("\\s+"));
      boolean stylesheet = rels.contains("stylesheet") || (rels.contains("preload") && as.equals("style"));
      if (stylesheet && href != null) {
        hrefs.add(href);
      }
    }
    return hrefs;
  }

  private static boolean isExternal(String url) {
    String u = url.toLowerCase();
    return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("//");
  }

  // style-src 의 도메인 항목(scheme 있음 · 없음, *.도메인)과 주소의 host 를 비교
  private static boolean allowedByStyleSrc(String url) {
    String host = URI.create(url.startsWith("//") ? "https:" + url : url).getHost().toLowerCase();
    for (String source : styleSrcSources()) {
      String s = source.replaceFirst("^https?://", "").replaceFirst("/.*$", "").toLowerCase();
      if (s.equals(host) || (s.startsWith("*.") && host.endsWith(s.substring(1)))) {
        return true;
      }
    }
    return false;
  }

  private static List<String> styleSrcSources() {
    for (String directive : SecurityHeadersFilter.CSP.split(";")) {
      String[] parts = directive.trim().split("\\s+");
      if (parts[0].equals("style-src")) {
        return Arrays.asList(parts).subList(1, parts.length);
      }
    }
    throw new AssertionError("CSP 에 style-src 가 없음");
  }

  private static List<Path> files(String extension) throws IOException {
    try (Stream<Path> paths = Files.walk(STATIC_DIR)) {
      List<Path> list = paths.filter(p -> p.toString().endsWith(extension)).toList();
      assertThat(list).as(STATIC_DIR + " 에서 " + extension + " 파일을 못 찾음").isNotEmpty();
      return list;
    }
  }
}
