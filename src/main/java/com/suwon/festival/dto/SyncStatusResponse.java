package com.suwon.festival.dto;

// GET /api/sync-status: 재단 동기화 마지막 성공 (한국 시간 "yyyy-MM-ddTHH:mm:ss", 없으면 모두 null)
// 실패 정보는 싣지 않음 (화면엔 마지막 성공 시각만)
public record SyncStatusResponse(String lastSuccessAt, Integer createdCount, Integer updatedCount) {

  public static SyncStatusResponse none() {
    return new SyncStatusResponse(null, null, null);
  }
}
