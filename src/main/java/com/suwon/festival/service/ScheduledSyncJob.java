package com.suwon.festival.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.instagram.InstagramSyncResult;
import com.suwon.festival.service.instagram.InstagramSyncService;
import com.suwon.festival.service.landmark.LandmarkSyncResult;
import com.suwon.festival.service.landmark.LandmarkSyncService;
import com.suwon.festival.service.merge.EventMergeRunner;
import com.suwon.festival.service.merge.MergeSummary;
import com.suwon.festival.service.place.EventDistrictFiller;

import lombok.RequiredArgsConstructor;

// 재단 → 인스타 → 구 정보 → 합치기를 한 작업으로 자동 실행. 재단 행사가 없는 DB 는 앱이 뜬 직후에도 1회 실행
// 명소(재단 59_C · 63_C)는 행사와 별개 단계: 하루 1회만 (LandmarkSyncService.isDue), 명소가 없는 DB 는 시작 직후에도
@Component
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ScheduledSyncJob {

  private static final Logger log = LoggerFactory.getLogger(ScheduledSyncJob.class);

  private final EventSyncService eventSyncService;
  private final InstagramSyncService instagramSyncService;
  private final EventMergeRunner eventMergeRunner;
  private final EventDistrictFiller eventDistrictFiller;
  private final EventRepository eventRepository;
  private final LandmarkSyncService landmarkSyncService;

  // 시작 직후 실행과 예약 실행이 겹치면 뒤의 것을 건너뜀 (서버 1대 기준)
  private final AtomicBoolean running = new AtomicBoolean();

  @Scheduled(cron = "${app.sync.cron}", zone = "Asia/Seoul")
  public void runScheduled() {
    runAll();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runIfEmpty() {
    // 인스타만 저장된 DB 도 포함 (첫 실행 때 재단만 실패한 경우)
    if (!eventRepository.existsBySource(Event.SOURCE_SWCF)) {
      log.info("재단 행사가 없는 DB → 시작 직후 동기화 실행");
      runAll();
    } else if (landmarkSyncService.isEmpty()) {
      log.info("명소가 없는 DB → 시작 직후 명소 동기화만 실행");
      runLandmarksOnly();
    }
  }

  void runLandmarksOnly() {
    if (!running.compareAndSet(false, true)) {
      log.info("동기화가 이미 실행 중 → 이번 명소 동기화는 건너뜀");
      return;
    }
    try {
      syncLandmarks();
    } finally {
      running.set(false);
    }
  }

  void runAll() {
    if (!running.compareAndSet(false, true)) {
      log.info("동기화가 이미 실행 중 → 이번 실행은 건너뜀");
      return;
    }
    try {
      boolean foundationOk = syncFoundation();
      boolean instagramOk = syncInstagram();
      boolean landmarkOk = syncLandmarks(); // 행사 칸과 무관 → 결과는 아래 한 줄에만
      // 앞 단계가 실패해도 저장된 행은 그대로라서 구 정보 · 합치기는 실행 (구 정보 실패는 다음 동기화 때 다시 → 결과에 넣지 않음)
      eventDistrictFiller.fillMissing();
      MergeSummary merge = eventMergeRunner.runAfterSync();
      if (foundationOk && instagramOk && landmarkOk && merge.ok()) {
        log.info("자동 동기화 완료 · {}", merge.text());
      } else {
        log.warn("자동 동기화 일부 실패 · 재단 {} · 인스타 {} · 명소 {} · {}", foundationOk ? "성공" : "실패",
            instagramOk ? "성공" : "실패", landmarkOk ? "성공" : "실패", merge.text());
      }
    } finally {
      running.set(false);
    }
  }

  private boolean syncFoundation() {
    try {
      SyncResult result = eventSyncService.syncEvents();
      if (result.alreadyRunning()) {
        log.info("재단 동기화 건너뜀 · 수동 동기화가 이미 실행 중");
        return true;
      }
      log.info("재단 동기화 · 새 행사 {}개, 갱신 {}개", result.saved(), result.updated());
      if (result.skipped() > 0) {
        log.warn("재단 동기화 · 형식 오류로 건너뛴 행사 {}개 (위 '재단 행사 건너뜀' 로그에 idx)", result.skipped());
      }
      return true;
    } catch (Exception e) {
      logFailure("재단", e);
      return false;
    }
  }

  private boolean syncInstagram() {
    try {
      InstagramSyncResult result = instagramSyncService.sync();
      if (result.configured()) {
        log.info("인스타 동기화 · 계정 {}/{}개 성공, 게시물 {}개", result.accountsOk(), result.accounts(), result.posts());
      } else {
        log.info("인스타 동기화 건너뜀 · {}", result.message());
      }
      return true;
    } catch (RuntimeException e) {
      logFailure("인스타", e);
      return false;
    }
  }

  // 하루 1회 (마지막 성공 20시간 안이면 건너뜀). 실패는 다음 자동 동기화에서 다시
  private boolean syncLandmarks() {
    try {
      if (!landmarkSyncService.isDue()) {
        log.info("명소 동기화 건너뜀 · 오늘 이미 받음");
        return true;
      }
      LandmarkSyncResult result = landmarkSyncService.sync();
      if (!result.configured() || result.alreadyRunning()) return true;
      if (result.ok()) {
        log.info("명소 동기화 · {}", result.text());
      } else {
        log.warn("명소 동기화 일부 실패 · {}", result.text());
      }
      return result.ok();
    } catch (RuntimeException e) {
      logFailure("명소", e);
      return false;
    }
  }

  // 메시지는 남기지 않음 (호출 주소의 API 키가 섞일 수 있음) → 예외와 원인의 종류만
  private void logFailure(String source, Exception e) {
    Throwable cause = e.getCause();
    log.warn("{} 동기화 실패 {}{}", source, e.getClass().getSimpleName(),
        cause == null ? "" : " ← " + cause.getClass().getSimpleName());
  }
}
