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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 수집한 인스타 게시물 1개의 처리 상태 (V2). 캡션 원문은 저장하지 않음 (설계 문서 8번)
// media_id 를 unique 로 둬서 다시 수집해도 같은 게시물이 두 번 들어오지 않음
@Entity
@Table(name = "instagram_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramMedia {

  public enum Status {
    COLLECTED, // 수집됨, 아직 정리 전
    PROCESSED, // 블록 정리까지 끝남 (블록별 결과는 instagram_block)
    FAILED     // 게시물 단위로 실패 (이유는 reason)
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "media_id", nullable = false, unique = true, length = 64)
  private String mediaId; // 인스타 게시물 id

  @Column(nullable = false, length = 30)
  private String account; // 게시한 계정 이름

  @Column(length = 1000)
  private String permalink;

  @Column
  private LocalDateTime postedAt; // 게시 시각 (한국 시간으로 바꿔 저장)

  // MySQL 에서 enum 칸이 아니라 VARCHAR 로 (V2 스크립트와 같게)
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(length = 255)
  private String reason;

  @Column(nullable = false)
  private LocalDateTime collectedAt;

  @Column
  private LocalDateTime processedAt;

  @Builder
  public InstagramMedia(String mediaId, String account, String permalink, LocalDateTime postedAt,
      LocalDateTime collectedAt) {
    this.mediaId = mediaId;
    this.account = account;
    this.permalink = permalink;
    this.postedAt = postedAt;
    this.collectedAt = collectedAt;
    this.status = Status.COLLECTED;
  }

  // 다시 수집했을 때 바뀔 수 있는 값만 갱신 (링크 · 게시 시각). 상태는 정리를 다시 거쳐야 하므로 COLLECTED 로
  public void recollect(String permalink, LocalDateTime postedAt, LocalDateTime at) {
    this.permalink = permalink;
    this.postedAt = postedAt;
    this.collectedAt = at;
    this.status = Status.COLLECTED;
  }

  public void markProcessed(LocalDateTime at) {
    this.status = Status.PROCESSED;
    this.reason = null;
    this.processedAt = at;
  }

  public void markFailed(String reason, LocalDateTime at) {
    this.status = Status.FAILED;
    this.reason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
    this.processedAt = at;
  }
}
