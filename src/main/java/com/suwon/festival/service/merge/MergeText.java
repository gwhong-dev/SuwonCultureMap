package com.suwon.festival.service.merge;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 제목 · 장소명 비교용 정리 (설계 3번 "정리 규칙"). 비교할 때만 쓰고 저장값은 바꾸지 않음
// `<2026 수원 국악 한마당> 🎶 #수원` → `2026수원국악한마당`
public final class MergeText {

  private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
  private static final Map<String, String> NAMED_ENTITIES = Map.of(
      "&amp;", "&", "&quot;", "\"", "&apos;", "'", "&lt;", "<", "&gt;", ">", "&nbsp;", " ");
  private static final Pattern HASHTAG = Pattern.compile("#[^\\s#]+");
  // 글자(한글 · 영문 등) · 숫자만 남김 → 공백 · 괄호류 · 따옴표 · 문장부호 · 이모지가 한 번에 빠짐
  private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

  private MergeText() {
  }

  public static String normalize(String s) {
    if (s == null) {
      return "";
    }
    String t = unescapeHtml(s);
    t = HASHTAG.matcher(t).replaceAll(" ");
    t = NOT_LETTER_OR_DIGIT.matcher(t).replaceAll("");
    return t.toLowerCase(Locale.ROOT);
  }

  // `&#39;` · `&#x27;` · `&amp;` 같은 HTML 표기 풀기
  static String unescapeHtml(String s) {
    String t = s;
    for (Map.Entry<String, String> e : NAMED_ENTITIES.entrySet()) {
      t = t.replace(e.getKey(), e.getValue());
    }
    Matcher m = NUMERIC_ENTITY.matcher(t);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String rep = m.group(0);
      try {
        int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
        if (Character.isValidCodePoint(cp)) {
          rep = new String(Character.toChars(cp));
        }
      } catch (NumberFormatException ignored) {
        // 10진수 자리에 a-f 가 섞인 경우 등 → 원문 유지
      }
      m.appendReplacement(out, Matcher.quoteReplacement(rep));
    }
    m.appendTail(out);
    return out.toString();
  }

  // 글자쌍(2글자씩) 유사도 = Dice 계수: 2 × 겹치는 쌍 수 / (양쪽 쌍 수 합). 0.0 ~ 1.0
  // 정리된 문자열을 넣을 것. 한쪽이 2글자 미만이면 완전히 같을 때만 1.0
  static double dice(String a, String b) {
    if (a.length() < 2 || b.length() < 2) {
      return a.equals(b) && !a.isEmpty() ? 1.0 : 0.0;
    }
    Map<String, Integer> pairs = new HashMap<>();
    for (int i = 0; i < a.length() - 1; i++) {
      pairs.merge(a.substring(i, i + 2), 1, Integer::sum);
    }
    int common = 0;
    for (int i = 0; i < b.length() - 1; i++) {
      String p = b.substring(i, i + 2);
      Integer n = pairs.get(p);
      if (n != null && n > 0) {
        common++;
        pairs.put(p, n - 1);
      }
    }
    return 2.0 * common / ((a.length() - 1) + (b.length() - 1));
  }
}
