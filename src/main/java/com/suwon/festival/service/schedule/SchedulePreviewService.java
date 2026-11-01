package com.suwon.festival.service.schedule;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.suwon.festival.dto.xml.ContentInfoDetail;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.InfoParser;

import lombok.RequiredArgsConstructor;

// 재단 XML 을 받아 진행 날짜 추출 결과를 보여 줌 (저장 안 함, 설계 11번 2번 PR)
// → 저장 전에 실데이터로 정답표(설계 부록)와 대조해 규칙을 조정
@Service
@RequiredArgsConstructor
public class SchedulePreviewService {

  private final EventSyncService eventSyncService;

  public SchedulePreviewResult preview() throws Exception {
    return fromXml(eventSyncService.fetchRawXml());
  }

  static SchedulePreviewResult fromXml(String xml) throws Exception {
    Map<Long, String> bodies = ScheduleBodyText.byIdx(xml);
    List<SchedulePreviewResult.Item> items = new ArrayList<>();
    int total = 0;
    int oneDay = 0;
    int dates = 0;
    int unknown = 0;

    for (EventItem item : EventSyncService.parseItems(xml)) {
      if (item.idx == null) continue; // 동기화도 건너뜀
      List<ContentInfoDetail> details = item.contentInfo == null ? null : item.contentInfo.contentInfoDetail;
      LocalDate start = item.eventDate == null ? null : date(item.eventDate.sdate);
      LocalDate end = item.eventDate == null ? null : date(item.eventDate.edate);
      String timeText = InfoParser.findTimeText(details);
      ScheduleResult r = ScheduleExtractor.extract(new ScheduleInput(start, end, timeText,
          InfoParser.findWeekdayText(details), bodies.get(item.idx)));

      total++;
      if (r.status() == ScheduleResult.Status.DATES) dates++; else unknown++;
      if (start != null && (end == null || end.equals(start))) {
        oneDay++;
        continue;
      }
      boolean onMap = item.coordinate != null && item.coordinate.lat != null && item.coordinate.lng != null;
      items.add(new SchedulePreviewResult.Item(item.idx, HtmlUtils.htmlUnescape(String.valueOf(item.title)), onMap,
          start, end, r.status(), r.dates(), r.holidayNote(), r.sourceText(), r.reason(), timeText));
    }
    return new SchedulePreviewResult(total, oneDay, dates, unknown, items);
  }

  private static LocalDate date(String value) {
    if (value == null) return null;
    try {
      return LocalDate.parse(value.strip());
    } catch (DateTimeException e) {
      return null;
    }
  }
}
