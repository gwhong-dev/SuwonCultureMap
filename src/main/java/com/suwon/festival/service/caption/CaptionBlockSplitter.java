package com.suwon.festival.service.caption;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 캡션을 행사 블록으로 나눈다 (월간 모음 글 = 한 글에 행사 여러 개)
//
// 실제 캡션 모양(설계 문서 3-1): 제목 줄 → 🗓️ 날짜 줄 → 📍 장소 줄 … 이 반복
// 규칙
// 1) "라벨 묶음" = 이어진 라벨 줄들. 그중 날짜 라벨이 있는 묶음 1개 = 행사 1개
// 2) 제목 = 묶음 바로 위의 (빈 줄 건너뛴) 첫 번째 일반 줄
// 3) 블록 원문 = 제목 줄부터 다음 블록 제목 줄 직전까지 (첫 제목 위 머리말은 제외)
// 4) 날짜 묶음이 하나도 없으면 캡션 전체가 블록 1개 (규칙으로는 못 읽음 → AI 몫)
// 5) 단일 행사 글(날짜 묶음 1개)에서 제목 줄에 작품명 괄호가 없고, 머리말에 작품명(<…> 〈…〉 《…》)이
//    딱 1종류 있으면 그 이름을 제목으로 (시설 계정 글은 라벨 위 줄이 소개 문장인 경우가 많음, 10/31 실측)
//    - 모음 글은 머리말 작품명이 "모음 제목"일 수 있어 적용 안 함
//    - 2종류 이상이면 어느 쪽인지 모름 → 기존 제목 줄 유지
public final class CaptionBlockSplitter {

  // 같은 줄 안에서 짝이 맞는 작품명 괄호. 안쪽 2~50자
  // 작품명이 아닌 괄호: HTML 태그(<br> </b> <a href=…>), 화살표(<- … ->), 링크 안내(<<프로필 링크>>)
  static final Pattern NOT_WORK_NAME = Pattern.compile(
      "^/?(?:br|b|i|u|a|p|em|strong|span|div|img|font|h[1-6])(?:\\s[\\x20-\\x7E]*)?/?$|^[-=~]|[-=~]$|링크|프로필|http", Pattern.CASE_INSENSITIVE);

  static final Pattern WORK_NAME = Pattern.compile("<([^<>\\n]{2,50})>|〈([^〈〉\\n]{2,50})〉|《([^《》\\n]{2,50})》");

  private CaptionBlockSplitter() {
  }

  public static List<CaptionBlock> split(String caption) {
    String normalized = CaptionText.normalize(caption).strip();
    if (normalized.isEmpty()) return List.of();
    List<String> lines = normalized.lines().toList();

    // 1) 날짜 라벨이 들어 있는 라벨 묶음 찾기 [시작, 끝)
    List<int[]> runs = new ArrayList<>();
    int i = 0;
    while (i < lines.size()) {
      if (CaptionLabel.of(lines.get(i)) == null) {
        i++;
        continue;
      }
      int start = i;
      boolean hasDate = false;
      while (i < lines.size() && CaptionLabel.of(lines.get(i)) != null) {
        if (CaptionLabel.of(lines.get(i)) == CaptionLabel.DATE) hasDate = true;
        i++;
      }
      if (hasDate) runs.add(new int[] { start, i });
    }

    if (runs.isEmpty()) {
      return List.of(new CaptionBlock(null, List.of(), normalized));
    }

    // 2) 묶음마다 제목 줄 위치 (이전 묶음 끝보다 아래에서만 찾음)
    int[] titleIdx = new int[runs.size()];
    for (int r = 0; r < runs.size(); r++) {
      int lowerBound = r == 0 ? 0 : runs.get(r - 1)[1];
      int t = runs.get(r)[0] - 1;
      while (t >= lowerBound && lines.get(t).isBlank()) t--;
      titleIdx[r] = (t >= lowerBound && CaptionLabel.of(lines.get(t)) == null) ? t : -1;
    }

    // 3) 블록 만들기
    List<CaptionBlock> blocks = new ArrayList<>();
    for (int r = 0; r < runs.size(); r++) {
      int[] run = runs.get(r);
      int from = titleIdx[r] >= 0 ? titleIdx[r] : run[0];
      int to;
      if (r + 1 < runs.size()) {
        to = titleIdx[r + 1] >= 0 ? titleIdx[r + 1] : runs.get(r + 1)[0];
      } else {
        to = lines.size();
      }
      String title = titleIdx[r] >= 0 ? lines.get(titleIdx[r]).strip() : null;
      if (runs.size() == 1 && title != null && workNames(title).isEmpty()) {
        Set<String> names = workNames(String.join("\n", lines.subList(0, titleIdx[r])));
        if (names.size() == 1) title = names.iterator().next();
      }
      List<String> labels = lines.subList(run[0], run[1]).stream().map(String::strip).toList();
      String text = String.join("\n", lines.subList(from, to)).strip();
      blocks.add(new CaptionBlock(title, labels, text));
    }
    return blocks;
  }

  // 글 안의 작품명(괄호 안쪽, 앞뒤 공백 정리) — 나온 순서대로, 중복 없이
  static Set<String> workNames(String text) {
    Set<String> names = new LinkedHashSet<>();
    Matcher m = WORK_NAME.matcher(text);
    while (m.find()) {
      String g = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
      String name = g.strip().replaceAll("\\s+", " ");
      if (name.codePoints().anyMatch(Character::isLetterOrDigit) && !NOT_WORK_NAME.matcher(name).find()) {
        names.add(name);
      }
    }
    return names;
  }
}
