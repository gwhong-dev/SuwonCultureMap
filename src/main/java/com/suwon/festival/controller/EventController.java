package com.suwon.festival.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.service.EventService;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.merge.EventMergeResult;
import com.suwon.festival.service.merge.EventMergeRunner;
import com.suwon.festival.service.merge.EventMergeService;
import com.suwon.festival.service.schedule.SchedulePreviewResult;
import com.suwon.festival.service.schedule.SchedulePreviewService;
import com.suwon.festival.dto.xml.EventItem;
import org.springframework.web.bind.annotation.PostMapping;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;
  private final EventSyncService eventSyncService;
  private final EventMergeService eventMergeService;
  private final EventMergeRunner eventMergeRunner;
  private final SchedulePreviewService schedulePreviewService;

  @GetMapping(produces = "application/json")
  public List<EventResponse> getEvents() {
    return eventService.getAllEvents();
  }

  // 같은 행사 합치기 결과 미리보기 (저장 안 함). 관리자 전용 보호는 PROGRESS "나중에 할 일"
  @GetMapping(value = "/merge/preview", produces = "application/json")
  public EventMergeResult mergePreview() {
    return eventMergeService.preview();
  }

  // 진행 날짜 추출 결과 미리보기 (저장 안 함, 재단 XML 을 그때 받음). 관리자 전용 보호는 위와 같음
  @GetMapping(value = "/schedule/preview", produces = "application/json")
  public SchedulePreviewResult schedulePreview() throws Exception {
    return schedulePreviewService.preview();
  }

  @GetMapping("/sync/raw")
  public String syncRaw() {
    return eventSyncService.fetchRawXml();
  }

  // @GetMapping("/sync/parsed")
  // public List<EventItem> syncParsed() throws Exception {
  // return eventSyncService.fetchEventItems();
  // }

  @GetMapping(value = "/sync/parsed", produces = "application/json")
  public List<EventItem> syncParsed() throws Exception {
    return eventSyncService.fetchEventItems();
  }

  @PostMapping("/sync")
  public String sync() throws Exception {
    SyncResult result;
    try {
      result = eventSyncService.syncEvents();
    } catch (Exception e) {
      // 행 하나가 실패해도 앞 행들은 이미 저장됨. 같은 행이 매번 실패하면 합치기가 계속 안 도니 여기서도 실행
      eventMergeRunner.runAfterSync();
      throw e;
    }
    return "새 행사 " + result.saved() + "개 저장 · 기존 행사 " + result.updated() + "개 갱신 완료\n"
        + eventMergeRunner.runAfterSync().text();
  }
}