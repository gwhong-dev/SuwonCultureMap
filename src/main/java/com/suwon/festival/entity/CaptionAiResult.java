package com.suwon.festival.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 캡션 AI 결과 보관 (V11). 같은 블록 원문이면 AI 를 다시 부르지 않음
//  - 키 = 블록 원문 해시 (원문 자체는 저장하지 않음)
//  - 호출 실패는 저장하지 않음 → 다음 동기화에서 다시 부름
@Entity
@Table(name = "caption_ai_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptionAiResult {

  public enum Outcome { EVENT, NOT_EVENT, UNREADABLE }

  public static final int TITLE_MAX = 255;
  public static final int CATEGORY_MAX = 50;
  public static final int TIME_TEXT_MAX = 1000;
  public static final int PLACE_MAX = 500;
  public static final int MODEL_MAX = 60;

  @Id
  @Column(name = "text_hash", length = 32)
  private String textHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private Outcome outcome;

  @Column(length = TITLE_MAX)
  private String title;

  @Column(length = CATEGORY_MAX)
  private String category;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "time_text", length = TIME_TEXT_MAX)
  private String timeText;

  @Column(name = "place_name", length = PLACE_MAX)
  private String placeName;

  @Column(nullable = false, length = MODEL_MAX)
  private String model;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  // 읽은 칸 (행사가 아닐 때 · 못 읽었을 때는 모두 null)
  public record Fields(String title, String category, LocalDate startDate, LocalDate endDate,
      String timeText, String placeName) {

    public static final Fields EMPTY = new Fields(null, null, null, null, null, null);
  }

  public static CaptionAiResult of(String textHash, Outcome outcome, Fields f, String model, LocalDateTime now) {
    CaptionAiResult r = new CaptionAiResult();
    r.textHash = textHash;
    r.outcome = outcome;
    if (outcome == Outcome.EVENT) {
      r.title = cut(f.title(), TITLE_MAX);
      r.category = cut(f.category(), CATEGORY_MAX);
      r.startDate = f.startDate();
      r.endDate = f.endDate();
      r.timeText = cut(f.timeText(), TIME_TEXT_MAX);
      r.placeName = cut(f.placeName(), PLACE_MAX);
    }
    r.model = cut(model, MODEL_MAX);
    r.createdAt = now;
    return r;
  }

  public Fields fields() {
    return new Fields(title, category, startDate, endDate, timeText, placeName);
  }

  // 칸 길이를 넘는 값은 잘라 저장 (꺼낼 때 검사기가 다시 확인). 이모지가 반으로 잘리지 않게 글자 단위
  private static String cut(String value, int max) {
    if (value == null || value.codePointCount(0, value.length()) <= max) return value;
    return value.substring(0, value.offsetByCodePoints(0, max));
  }
}
