package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.List;

// 뽑은 날짜 목록을 코드가 다시 검사 (설계 4번). 날짜 하나라도 틀리면 목록 전체를 버림 → 일부만 맞는 목록은 빠진 날을 "안 함"으로 안내하게 됨
// 요일이 달력과 맞는지는 원문 조각이 필요해 ScheduleExtractor 가 읽으면서 확인함. AI 를 붙일 때는 이 검사와 별도로 원문 대조가 필요
public final class ScheduleValidator {

  private ScheduleValidator() {
  }

  // 문제가 있으면 이유, 없으면 null. expectedCount: 원문의 "총 N회" (없으면 null)
  public static String check(LocalDate start, LocalDate end, List<LocalDate> dates, Integer expectedCount) {
    if (dates.isEmpty()) return "기간 안에 해당 날짜가 없음";
    for (LocalDate date : dates) {
      if (date.isBefore(start) || date.isAfter(end)) return "기간 밖 날짜: " + date;
    }
    if (expectedCount != null && expectedCount != dates.size()) {
      return "총 " + expectedCount + "회 표기와 날짜 수(" + dates.size() + ")가 다름";
    }
    return null;
  }
}
