package com.suwon.festival.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.service.instagram.InstagramSyncResult;
import com.suwon.festival.service.instagram.InstagramSyncService;

import lombok.RequiredArgsConstructor;

// 인스타 게시물 → 행사 동기화를 손으로 실행 (재단 동기화 POST /api/events/sync 와 같은 방식)
// 자동 실행(스케줄러) · 관리자 전용 보호는 PROGRESS "나중에 할 일"
@RestController
@RequestMapping("/api/instagram")
@RequiredArgsConstructor
public class InstagramController {

  private final InstagramSyncService instagramSyncService;

  @PostMapping(value = "/sync", produces = "application/json")
  public InstagramSyncResult sync() {
    return instagramSyncService.sync();
  }
}
