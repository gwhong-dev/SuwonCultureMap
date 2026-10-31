package com.suwon.festival.service.caption;

import java.util.regex.Pattern;

// 캡션 글자 정리 도구 (비교 전에 모양을 통일)
public final class CaptionText {

  private CaptionText() {
  }

  // 이모지 변형 문자: "🗓️" = "🗓" + U+FE0F. 붙을 때도 안 붙을 때도 있어서 지우고 비교
  private static final Pattern VARIATION_SELECTOR = Pattern.compile("[\\uFE0E\\uFE0F]");
  // 전각·물결 변형 → "~"
  private static final Pattern TILDE = Pattern.compile("[～〜∼~]");
  // 폭 없는 문자(줄 앞에 숨어 있으면 라벨 인식이 깨짐)
  private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]");

  public static String normalize(String value) {
    if (value == null) return "";
    String v = value.replace("\r\n", "\n").replace('\r', '\n');
    v = VARIATION_SELECTOR.matcher(v).replaceAll("");
    v = ZERO_WIDTH.matcher(v).replaceAll("");
    v = v.replace(' ', ' ').replace('　', ' ');
    v = TILDE.matcher(v).replaceAll("~");
    return v;
  }

  // 글자 수 제한 (이모지처럼 2칸짜리 글자가 반으로 잘리지 않게)
  public static String limit(String value, int max) {
    if (value == null || value.length() <= max) return value;
    int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
    return value.substring(0, end);
  }

  // 비어 있으면 null
  public static String blankToNull(String value) {
    if (value == null) return null;
    String v = value.strip();
    return v.isEmpty() ? null : v;
  }
}
