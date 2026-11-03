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

// CSP script-src 에 'unsafe-inline' 이 없으므로 인라인 스크립트가 다시 들어오면 운영에서 그 기능만 조용히 멈춤 → 미리 막음
class StaticScriptPolicyTest {

  private static final Path STATIC_DIR = Path.of("src/main/resources/static");

  // src 없는 <script> (인라인 스크립트)
  private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
  // onclick= · onerror= 같은 이벤트 속성. JS 문자열로 만든 HTML(innerHTML) 안의 것도 잡도록 앞 글자는 공백 · 따옴표
  private static final Pattern EVENT_ATTR = Pattern.compile("[\\s\"']on[a-z]+\\s*=\\s*\\\\?[\"']", Pattern.CASE_INSENSITIVE);
  // 문자열을 코드로 실행 (CSP 에 'unsafe-eval' 이 없어 막힘)
  private static final Pattern EVAL = Pattern.compile("\\beval\\s*\\(|new\\s+Function\\s*\\(|set(Timeout|Interval)\\s*\\(\\s*[\"'`]");

  @Test
  void HTML에_인라인_스크립트와_on속성이_없음() throws IOException {
    List<String> found = new ArrayList<>();
    for (Path file : files(".html")) {
      String html = Files.readString(file, StandardCharsets.UTF_8);
      collect(found, file, html, INLINE_SCRIPT);
      collect(found, file, html, EVENT_ATTR);
    }
    assertThat(found).as("static/js 로 옮기고 addEventListener 로 연결").isEmpty();
  }

  @Test
  void JS가_만드는_HTML에도_on속성과_eval이_없음() throws IOException {
    List<String> found = new ArrayList<>();
    for (Path file : files(".js")) {
      String js = Files.readString(file, StandardCharsets.UTF_8);
      collect(found, file, js, EVENT_ATTR);
      collect(found, file, js, EVAL);
    }
    assertThat(found).as("만든 요소에 addEventListener 로 연결 (index.js 의 bindOpenDetail · hideOnError 참고)").isEmpty();
  }

  @Test
  void 검사식이_실제로_잡아냄() {
    // 위 두 시험이 아무것도 못 잡는 검사식 때문에 통과하는 일이 없게
    assertThat(INLINE_SCRIPT.matcher("<script>alert(1)</script>").find()).isTrue();
    assertThat(INLINE_SCRIPT.matcher("<script src=\"/js/index.js\"></script>").find()).isFalse();
    assertThat(EVENT_ATTR.matcher("<div onclick=\"go()\">").find()).isTrue();
    assertThat(EVENT_ATTR.matcher("'<img onerror=\\'x\\'>'").find()).isTrue();
    assertThat(EVENT_ATTR.matcher("btn.onclick = function () {}").find()).isFalse();
    assertThat(EVAL.matcher("setTimeout(\"go()\", 10)").find()).isTrue();
    assertThat(EVAL.matcher("setTimeout(function () {}, 10)").find()).isFalse();
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
