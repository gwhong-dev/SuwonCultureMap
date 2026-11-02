package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.List;

// 진행 날짜 미리보기 결과. items 는 여러 날 행사만 (하루짜리는 규칙 없이 정해지므로 개수만)
// noBody: 본문을 못 찾은 행사 수. 재단 형식이 바뀌면 조용히 모름만 늘어나므로 눈으로 확인할 수 있게 둠
public record SchedulePreviewResult(int total, int oneDay, int dates, int unknown, int noBody, List<Item> items) {

  public record Item(Long idx, String title, boolean onMap, LocalDate start, LocalDate end,
      ScheduleResult.Status status, List<LocalDate> dates, boolean holidayNote,
      String sourceText, String reason, String timeText) {
  }
}
