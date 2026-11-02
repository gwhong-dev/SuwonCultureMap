package com.suwon.festival.service.schedule;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

import com.suwon.festival.dto.xml.ContentInfoDetail;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.service.InfoParser;

// 재단 행사 한 건의 칸 값 + 본문 → 진행 날짜. 미리보기와 동기화 저장이 같은 결과를 내도록 한곳에 둠
public final class ScheduleReader {

  private ScheduleReader() {
  }

  public static ScheduleResult read(EventItem item, String body) {
    LocalDate start = item.eventDate == null ? null : date(item.eventDate.sdate);
    LocalDate end = item.eventDate == null ? null : date(item.eventDate.edate);
    // 시작일만 읽히면 추출기는 하루짜리로 보지만, 재단 데이터는 종료일이 늘 있음 → 못 읽은 것은 이상 데이터로 드러냄
    if (start != null && end == null) return ScheduleResult.unknown("종료일을 못 읽음");
    List<ContentInfoDetail> details = details(item);
    return ScheduleExtractor.extract(new ScheduleInput(start, end,
        InfoParser.findFullTimeText(details), InfoParser.findWeekdayText(details), body));
  }

  static List<ContentInfoDetail> details(EventItem item) {
    return item.contentInfo == null ? null : item.contentInfo.contentInfoDetail;
  }

  static LocalDate date(String value) {
    if (value == null) return null;
    try {
      return LocalDate.parse(value.strip());
    } catch (DateTimeException e) {
      return null;
    }
  }
}
