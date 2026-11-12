package com.suwon.festival.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.SyncStatusResponse;
import com.suwon.festival.service.SyncLogService;

import lombok.RequiredArgsConstructor;

// 화면의 "11/11 06:00 갱신" 줄용 공개 API (로그인 불필요, 운영에도 등록). 재단 마지막 성공 + 그 뒤 연속 실패 횟수
// (동기화 실패 알림 워크플로 sync-alert.yml 용) — 실패는 횟수만, 오류 내용은 안 보냄
@RestController
@RequiredArgsConstructor
public class SyncStatusController {

  private final SyncLogService syncLogService;

  @GetMapping(value = "/api/sync-status", produces = "application/json")
  public SyncStatusResponse syncStatus() {
    return syncLogService.foundationStatus();
  }
}
