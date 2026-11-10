package com.suwon.festival.controller;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.merge.EventMergeResult;
import com.suwon.festival.service.merge.EventMergeRunner;
import com.suwon.festival.service.merge.EventMergeService;
import com.suwon.festival.service.place.EventDistrictFiller;
import com.suwon.festival.service.schedule.SchedulePreviewResult;
import com.suwon.festival.service.schedule.SchedulePreviewService;

import lombok.RequiredArgsConstructor;

// 개발 확인용 수동 동기화 · 미리보기. 운영(prod)에는 등록되지 않음 → 운영은 ScheduledSyncJob 이 자동 실행
@RestController
@RequestMapping("/api/events")
@Profile("(dev | test) & !prod")
@RequiredArgsConstructor
public class ManualSyncController {

  private final EventSyncService eventSyncService;
  private final EventMergeService eventMergeService;
  private final EventMergeRunner eventMergeRunner;
  private final EventDistrictFiller eventDistrictFiller;
  private final SchedulePreviewService schedulePreviewService;

  // 같은 행사 합치기 결과 미리보기 (저장 안 함)
  @GetMapping(value = "/merge/preview", produces = "application/json")
  public EventMergeResult mergePreview() {
    return eventMergeService.preview();
  }

  // 진행 날짜 추출 결과 미리보기 (저장 안 함, 재단 XML 을 그때 받음)
  @GetMapping(value = "/schedule/preview", produces = "application/json")
  public SchedulePreviewResult schedulePreview() throws Exception {
    return schedulePreviewService.preview();
  }

  @GetMapping("/sync/raw")
  public String syncRaw() {
    return eventSyncService.fetchRawXml();
  }

  @GetMapping(value = "/sync/parsed", produces = "application/json")
  public List<EventItem> syncParsed() throws Exception {
    return eventSyncService.fetchEventItems();
  }

  @PostMapping("/sync")
  public ResponseEntity<String> sync() throws Exception {
    SyncResult result;
    try {
      result = eventSyncService.syncEvents();
    } catch (Exception e) {
      // 행 하나가 실패해도 앞 행들은 이미 저장됨. 같은 행이 매번 실패하면 합치기가 계속 안 도니 여기서도 실행
      eventDistrictFiller.fillMissing();
      eventMergeRunner.runAfterSync();
      throw e;
    }
    // 예약 동기화가 돌고 있으면 409 + 안내 (그쪽이 구 정보 · 합치기까지 함)
    if (result.alreadyRunning()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body("재단 동기화가 이미 실행 중입니다. 끝난 뒤 다시 시도해 주세요.");
    }
    String district = eventDistrictFiller.fillMissing().text();
    return ResponseEntity.ok("새 행사 " + result.saved() + "개 저장 · 기존 행사 " + result.updated() + "개 갱신 완료"
        + (result.skipped() > 0 ? " · 형식 오류로 " + result.skipped() + "개 건너뜀" : "") + "\n"
        + district + "\n"
        + eventMergeRunner.runAfterSync().text());
  }
}
