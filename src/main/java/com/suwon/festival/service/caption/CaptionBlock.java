package com.suwon.festival.service.caption;

import java.util.List;

// 캡션 안의 행사 후보 1개
// title     : 라벨 줄 바로 위의 제목 줄 (없으면 null)
// labelLines: 🗓️ · 📍 같은 라벨 줄 (없으면 빈 목록 → 규칙으로 못 읽음)
// text      : 이 블록의 원문 (AI 입력 · 원문 대조에 사용). 캡션 전체가 아니라 블록만
public record CaptionBlock(String title, List<String> labelLines, String text) {

  public CaptionBlock {
    labelLines = List.copyOf(labelLines);
  }
}
