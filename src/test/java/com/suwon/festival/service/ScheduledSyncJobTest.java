package com.suwon.festival.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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

// 자동 동기화 순서 · 실패 분리 · 겹침 방지 · 빈 DB 시작 실행
class ScheduledSyncJobTest {

  private final EventSyncService eventSyncService = mock(EventSyncService.class);
  private final InstagramSyncService instagramSyncService = mock(InstagramSyncService.class);
  private final EventMergeRunner eventMergeRunner = mock(EventMergeRunner.class);
  private final EventDistrictFiller eventDistrictFiller = mock(EventDistrictFiller.class);
  private final EventRepository eventRepository = mock(EventRepository.class);
  private final LandmarkSyncService landmarkSyncService = mock(LandmarkSyncService.class);
  private final ScheduledSyncJob job = new ScheduledSyncJob(eventSyncService, instagramSyncService, eventMergeRunner,
      eventDistrictFiller, eventRepository, landmarkSyncService);

  @BeforeEach
  void setUp() throws Exception {
    when(eventSyncService.syncEvents()).thenReturn(new SyncResult(1, 2));
    when(instagramSyncService.sync())
        .thenReturn(new InstagramSyncResult(true, null, 1, 1, List.of(), 1, 1, 0, Map.of("READY", 3), Map.of(), 3, 0, 0, null, null));
    when(eventMergeRunner.runAfterSync()).thenReturn(new MergeSummary(true, 0, 0, 0));
    when(landmarkSyncService.isDue()).thenReturn(true);
    when(landmarkSyncService.sync()).thenReturn(new LandmarkSyncResult(true, false, 75, 75, 0, 0, 2, 8, List.of(), List.of()));
  }

  @Test
  void 명소는_하루_한_번_때가_됐을_때만() throws Exception {
    job.runScheduled();
    verify(landmarkSyncService).sync();

    when(landmarkSyncService.isDue()).thenReturn(false);
    job.runScheduled();
    verify(landmarkSyncService, times(1)).sync(); // 두 번째는 건너뜀
    verify(eventSyncService, times(2)).syncEvents(); // 행사는 매번
  }

  @Test
  void 명소가_실패해도_구정보와_합치기는_실행() {
    when(landmarkSyncService.sync()).thenThrow(new IllegalStateException("가상 실패"));

    job.runScheduled();

    verify(eventDistrictFiller).fillMissing();
    verify(eventMergeRunner).runAfterSync();
  }

  @Test
  void 행사는_있고_명소가_없는_DB는_시작_직후_명소만() throws Exception {
    when(eventRepository.existsBySource(Event.SOURCE_SWCF)).thenReturn(true);
    when(landmarkSyncService.isEmpty()).thenReturn(true);

    job.runIfEmpty();

    verify(landmarkSyncService).sync();
    verify(eventSyncService, never()).syncEvents();
    verify(eventMergeRunner, never()).runAfterSync();
  }

  @Test
  void 재단_인스타_구정보_합치기_순서로_실행() throws Exception {
    job.runScheduled();

    InOrder order = inOrder(eventSyncService, instagramSyncService, eventDistrictFiller, eventMergeRunner);
    order.verify(eventSyncService).syncEvents();
    order.verify(instagramSyncService).sync();
    order.verify(eventDistrictFiller).fillMissing();
    order.verify(eventMergeRunner).runAfterSync();
  }

  @Test
  void 재단이_실패해도_인스타와_합치기는_실행() throws Exception {
    when(eventSyncService.syncEvents()).thenThrow(new IllegalStateException("가상 실패"));

    job.runScheduled();

    verify(instagramSyncService).sync();
    verify(eventDistrictFiller).fillMissing();
    verify(eventMergeRunner).runAfterSync();
  }

  @Test
  void 인스타가_실패해도_합치기는_실행() {
    when(instagramSyncService.sync()).thenThrow(new IllegalStateException("가상 실패"));

    job.runScheduled();

    verify(eventMergeRunner).runAfterSync();
  }

  @Test
  void 실패_뒤_다음_실행은_정상_동작() throws Exception {
    when(eventMergeRunner.runAfterSync())
        .thenThrow(new IllegalStateException("가상 실패"))
        .thenReturn(new MergeSummary(true, 0, 0, 0));

    try {
      job.runScheduled();
    } catch (IllegalStateException expected) {
      // 잠금이 풀렸는지만 확인
    }
    job.runScheduled();

    verify(eventSyncService, times(2)).syncEvents();
  }

  @Test
  void 재단_수동_동기화가_도는_중이면_재단만_건너뛰고_나머지는_실행() throws Exception {
    when(eventSyncService.syncEvents()).thenReturn(SyncResult.skippedAlreadyRunning());

    job.runScheduled();

    verify(instagramSyncService).sync();
    verify(eventDistrictFiller).fillMissing();
    verify(eventMergeRunner).runAfterSync();
  }

  @Test
  void 실행_중이면_겹쳐_실행하지_않음() throws Exception {
    doAnswer(invocation -> {
      job.runScheduled(); // 실행 도중 예약 시각이 된 상황
      return new SyncResult(0, 0);
    }).when(eventSyncService).syncEvents();

    job.runScheduled();

    verify(eventSyncService, times(1)).syncEvents();
  }

  @Test
  void 재단_행사가_없는_DB는_시작_직후_실행() throws Exception {
    when(eventRepository.existsBySource(Event.SOURCE_SWCF)).thenReturn(false);

    job.runIfEmpty();

    verify(eventSyncService).syncEvents();
  }

  @Test
  void 재단_행사가_있는_DB는_시작_때_실행하지_않음() throws Exception {
    when(eventRepository.existsBySource(Event.SOURCE_SWCF)).thenReturn(true);

    job.runIfEmpty();

    verify(eventSyncService, never()).syncEvents();
  }
}
