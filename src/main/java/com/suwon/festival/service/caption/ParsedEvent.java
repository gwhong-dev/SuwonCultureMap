package com.suwon.festival.service.caption;

import java.time.LocalDate;

// 블록 1개를 정리한 결과 (규칙 · AI 공통 모양, 설계 문서 5번 출력 칸)
// 모르는 칸은 null. isEvent 가 null 이면 "행사인지 모름"
// scheduleText: 날짜 줄 원문 (라벨 뗌). 저장할 때 진행 날짜(여러 회차 · 매주 요일)를 이 줄에서 다시 읽음
public record ParsedEvent(
    Boolean isEvent,
    String title,
    String category,
    LocalDate startDate,
    LocalDate endDate,
    String timeText,
    String placeName,
    String scheduleText) {

  // 날짜 줄 원문이 없는 결과 (AI · 시험)
  public ParsedEvent(Boolean isEvent, String title, String category, LocalDate startDate, LocalDate endDate,
      String timeText, String placeName) {
    this(isEvent, title, category, startDate, endDate, timeText, placeName, null);
  }
}
