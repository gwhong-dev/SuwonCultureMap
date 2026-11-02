package com.suwon.festival.dto;

// 행사 동기화 결과: 새로 저장한 개수 + 기존 행사 중 갱신한 개수 + 형식 오류로 건너뛴 개수
public record SyncResult(int saved, int updated, int skipped) {

  public SyncResult(int saved, int updated) {
    this(saved, updated, 0);
  }
}
