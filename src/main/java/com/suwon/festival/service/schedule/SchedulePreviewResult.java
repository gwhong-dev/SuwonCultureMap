package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.List;

// 진행 날짜 미리보기 결과. items 는 여러 날 행사만 (하루짜리는 규칙 없이 정해지므로 개수만)
public record SchedulePreviewResult(int total, int oneDay, int dates, int unknown, List<Item> items) {

  public record Item(Long idx, String title, boolean onMap, LocalDate start, LocalDate end,
      ScheduleResult.Status status, List<LocalDate> dates, boolean holidayNote,
      String sourceText, String reason, String timeText) {
  }
}
