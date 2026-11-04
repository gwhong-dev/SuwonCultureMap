package com.suwon.festival.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.service.instagram.InstagramSyncResult;
import com.suwon.festival.service.instagram.InstagramSyncService;
import com.suwon.festival.service.merge.EventMergeRunner;
import com.suwon.festival.service.place.EventDistrictFiller;

import lombok.RequiredArgsConstructor;

// 인스타 게시물 → 행사 동기화를 손으로 실행 (재단 동기화 POST /api/events/sync 와 같은 방식)
// 개발 확인용. 운영(prod)에는 등록되지 않음 → 운영은 ScheduledSyncJob 이 자동 실행
@RestController
@RequestMapping("/api/instagram")
@Profile("(dev | test) & !prod")
@RequiredArgsConstructor
public class InstagramController {

  private final InstagramSyncService instagramSyncService;
  private final EventMergeRunner eventMergeRunner;
  private final EventDistrictFiller eventDistrictFiller;

  @PostMapping(value = "/sync", produces = "application/json")
  public InstagramSyncResult sync() {
    // 계정 일부가 실패해 일찍 끝나도 합치기는 실행 (저장된 게시물은 그대로라서)
    InstagramSyncResult result = instagramSyncService.sync();
    eventDistrictFiller.fillMissing();
    return result.withMerge(eventMergeRunner.runAfterSync());
  }
}
