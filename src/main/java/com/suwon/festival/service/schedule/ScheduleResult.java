package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.List;

// 진행 날짜 추출 결과
// DATES  : dates 가 정답 (기간 중 나머지 날은 진행하지 않음)
// UNKNOWN: 기간만 믿을 수 있음 → 화면은 원문 표시. "매일 진행"이라는 뜻이 아님
// holidayNote: 원문에 공휴일 조건이 있음. 달력 없이 요일로만 펼쳤으므로 "공휴일은 원문 확인" 표시 (설계 6번)
// reason: UNKNOWN 인 이유 (미리보기 · 로그용)
public record ScheduleResult(Status status, List<LocalDate> dates, String sourceText, boolean holidayNote,
    String reason) {

  public enum Status { DATES, UNKNOWN }

  public ScheduleResult {
    dates = List.copyOf(dates);
  }

  static ScheduleResult dates(List<LocalDate> dates, String sourceText, boolean holidayNote) {
    return new ScheduleResult(Status.DATES, dates, sourceText, holidayNote, null);
  }

  static ScheduleResult unknown(String reason) {
    return new ScheduleResult(Status.UNKNOWN, List.of(), null, false, reason);
  }
}
