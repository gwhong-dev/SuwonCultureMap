package com.suwon.festival.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 게시물 안 행사 후보(블록) 1개의 처리 상태 (V2, 설계 문서 4번 "상태는 블록 단위")
// 지도에 올라간 블록은 event 를 가리킴. (게시물, 블록 키) 는 unique
@Entity
@Table(name = "instagram_block", uniqueConstraints = @UniqueConstraint(columnNames = { "media_id", "block_key" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramBlock {

  // 설계 문서 4번 상태값 (캡션 정리 단계의 CandidateStatus + 좌표 · 노출 단계)
  public enum Status {
    PARSED, NO_DATE, PENDING, FAILED, SKIPPED, // 캡션 정리 결과
    NO_COORD, // 좌표를 못 찾아 보류
    ERROR,    // 장소 검색 호출 실패 → 다음 실행에서 다시
    READY     // 지도에 올라감 (event 연결됨)
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "media_id", nullable = false)
  private InstagramMedia media;

  @Column(name = "block_key", nullable = false, length = 100)
  private String blockKey; // 블록 구분 키 (제목 + 시작일 + 장소명 기반, 5단계에서 만듦)

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(length = 255)
  private String reason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private Event event; // READY 일 때만

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public InstagramBlock(InstagramMedia media, String blockKey, Status status, String reason, LocalDateTime updatedAt) {
    this.media = media;
    this.blockKey = blockKey;
    this.status = status;
    this.reason = cut(reason);
    this.updatedAt = updatedAt;
  }

  public void update(Status status, String reason, Event event, LocalDateTime at) {
    this.status = status;
    this.reason = cut(reason);
    this.event = event;
    this.updatedAt = at;
  }

  private static String cut(String reason) {
    return reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
  }
}
