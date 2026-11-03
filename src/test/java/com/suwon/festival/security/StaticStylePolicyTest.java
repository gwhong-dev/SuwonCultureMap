package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// CSP style-src 를 'self' 로 좁히려면 인라인 스타일이 없어야 함 → 화면 스타일은 static/css 로 분리하고 다시 들어오지 않게 막음
// (JS 의 el.style.display = ... 같은 CSSOM 변경은 CSP 가 막지 않아 그대로 둠)
class StaticStylePolicyTest {

  private static final Path STATIC_DIR = Path.of("src/main/resources/static");

  // <style> 태그
  private static final Pattern STYLE_TAG = Pattern.compile("<style[\\s>]", Pattern.CASE_INSENSITIVE);
  // style="..." 속성. JS 문자열로 만든 HTML(innerHTML) 안의 것도 잡도록 앞 글자는 공백, 따옴표는 \" 도 허용
  private static final Pattern STYLE_ATTR = Pattern.compile("\\sstyle\\s*=\\s*\\\\?[\"']", Pattern.CASE_INSENSITIVE);
  // setAttribute("style", ...) 도 인라인 스타일
  private static final Pattern SET_STYLE_ATTR = Pattern.compile("setAttribute\\(\\s*[\"']style[\"']", Pattern.CASE_INSENSITIVE);

  @Test
  void HTML에_style태그와_style속성이_없음() throws IOException {
    List<String> found = new ArrayList<>();
    for (Path file : files(".html")) {
      String html = Files.readString(file, StandardCharsets.UTF_8);
      collect(found, file, html, STYLE_TAG);
      collect(found, file, html, STYLE_ATTR);
    }
    assertThat(found).as("static/css 로 옮기고 class 로 연결").isEmpty();
  }

  @Test
  void JS가_만드는_HTML에도_style속성이_없음() throws IOException {
    List<String> found = new ArrayList<>();
    for (Path file : files(".js")) {
      String js = Files.readString(file, StandardCharsets.UTF_8);
      collect(found, file, js, STYLE_TAG);
      collect(found, file, js, STYLE_ATTR);
      collect(found, file, js, SET_STYLE_ATTR);
    }
    assertThat(found).as("class 를 붙이고 css/index.css 에 규칙 추가 (iw-* 참고)").isEmpty();
  }

  @Test
  void 화면마다_자기_css_파일을_불러옴() throws IOException {
    for (String name : List.of("index", "about", "login", "mypage", "privacy")) {
      String html = Files.readString(STATIC_DIR.resolve(name + ".html"), StandardCharsets.UTF_8);
      assertThat(html).as(name).contains("<link rel=\"stylesheet\" href=\"/css/" + name + ".css\" />");
      assertThat(STATIC_DIR.resolve("css/" + name + ".css")).as(name).isRegularFile();
    }
  }

  @Test
  void 검사식이_실제로_잡아냄() {
    // 위 시험이 아무것도 못 잡는 검사식 때문에 통과하는 일이 없게
    assertThat(STYLE_TAG.matcher("<style>body{}</style>").find()).isTrue();
    assertThat(STYLE_TAG.matcher("<link rel=\"stylesheet\" href=\"/css/index.css\" />").find()).isFalse();
    assertThat(STYLE_ATTR.matcher("<div style=\"display: none\">").find()).isTrue();
    assertThat(STYLE_ATTR.matcher("'<span style=\"color:#F5A623;\">'").find()).isTrue();
    assertThat(STYLE_ATTR.matcher("\"<div style=\\\"width:1px\\\">\"").find()).isTrue();
    assertThat(STYLE_ATTR.matcher("el.style.display = \"none\"").find()).isFalse();
    assertThat(SET_STYLE_ATTR.matcher("el.setAttribute(\"style\", \"color:red\")").find()).isTrue();
  }

  private static List<Path> files(String extension) throws IOException {
    try (Stream<Path> paths = Files.walk(STATIC_DIR)) {
      List<Path> list = paths.filter(p -> p.toString().endsWith(extension)).toList();
      assertThat(list).as(STATIC_DIR + " 에서 " + extension + " 파일을 못 찾음").isNotEmpty();
      return list;
    }
  }

  private static void collect(List<String> found, Path file, String text, Pattern pattern) {
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      int line = text.substring(0, m.start()).split("\n", -1).length;
      found.add(STATIC_DIR.relativize(file) + ":" + line + " " + m.group().trim());
    }
  }
}
