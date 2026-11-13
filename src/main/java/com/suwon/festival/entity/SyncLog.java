package com.suwon.festival.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 동기화 1회 이력 (V8). 화면의 "데이터 갱신 시각"은 재단(SWCF) 마지막 SUCCESS 의 finishedAt
// errorMessage 에는 예외 종류 · 실패 개수만 (예외 메시지 · 호출 주소 · 키 X — SyncLogService.errorText)
@Entity
@Table(name = "sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncLog {

  // LANDMARK = 재단 명소 59_C · 63_C (V10, 하루 1회 — LandmarkSyncService). 칸이 VARCHAR 라 값 추가에 DB 스크립트가 필요 없음
  // SWDB = 수원문화DB 행사정보 (하루 1회 — SwdbSyncService)
  public enum Source { SWCF, INSTAGRAM, LANDMARK, SWDB }

  public enum Status { SUCCESS, FAIL }

  public static final int ERROR_MAX = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // MySQL 에서 enum 칸이 아니라 VARCHAR 로 (V8 스크립트와 같게)
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private Source source;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt; // 한국 시간

  @Column(name = "finished_at", nullable = false)
  private LocalDateTime finishedAt; // 한국 시간

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "fetched_count")
  private Integer fetchedCount; // 받은 개수 (재단 = 행사, 인스타 = 게시물). 못 세면 null

  @Column(name = "created_count")
  private Integer createdCount;

  @Column(name = "updated_count")
  private Integer updatedCount;

  @Column(name = "error_message", length = ERROR_MAX)
  private String errorMessage;

  public SyncLog(Source source, LocalDateTime startedAt, LocalDateTime finishedAt, Status status,
      Integer fetchedCount, Integer createdCount, Integer updatedCount, String errorMessage) {
    this.source = source;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.status = status;
    this.fetchedCount = fetchedCount;
    this.createdCount = createdCount;
    this.updatedCount = updatedCount;
    this.errorMessage = errorMessage;
  }
}
