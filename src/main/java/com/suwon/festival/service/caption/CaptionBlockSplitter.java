package com.suwon.festival.service.caption;

import java.util.ArrayList;
import java.util.List;

// 캡션을 행사 블록으로 나눈다 (월간 모음 글 = 한 글에 행사 여러 개)
//
// 실제 캡션 모양(설계 문서 3-1): 제목 줄 → 🗓️ 날짜 줄 → 📍 장소 줄 … 이 반복
// 규칙
// 1) "라벨 묶음" = 이어진 라벨 줄들. 그중 날짜 라벨이 있는 묶음 1개 = 행사 1개
// 2) 제목 = 묶음 바로 위의 (빈 줄 건너뛴) 첫 번째 일반 줄
// 3) 블록 원문 = 제목 줄부터 다음 블록 제목 줄 직전까지 (첫 제목 위 머리말은 제외)
// 4) 날짜 묶음이 하나도 없으면 캡션 전체가 블록 1개 (규칙으로는 못 읽음 → AI 몫)
public final class CaptionBlockSplitter {

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
      List<String> labels = lines.subList(run[0], run[1]).stream().map(String::strip).toList();
      String text = String.join("\n", lines.subList(from, to)).strip();
      blocks.add(new CaptionBlock(title, labels, text));
    }
    return blocks;
  }
}
