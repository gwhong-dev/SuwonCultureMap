package com.suwon.festival.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 우리 서비스 내부 PK. 재단 API와 무관한 우리 DB 전용 식별자

  @Column(nullable = false, unique = true)
  private Long idx;
  // 재단 원본 데이터의 PK. unique 필수!
  // 이게 없으면 배치 스케줄러가 재단 API를 반복 호출할 때
  // 같은 이벤트가 계속 중복 저장됨 (가이드에서 "제일 흔한 실수"로 지적한 부분)

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String category;
  // Enum이 아니라 String으로 결정한 이유:
  // 카테고리 값은 재단이 정하는 외부 종속값 → 재단이 새 카테고리를 추가하면
  // Enum에는 없는 값이 들어와서 매핑 에러가 남. 통제 못하는 값이라 String이 안전

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;
  // Coordinate라는 @Embeddable 클래스로 분리할 수도 있지만,
  // 필드 2개뿐이라 초보 단계에서는 Double 2개로 단순하게 유지

  @Column(length = 1000)
  private String imageUrl;
  // 원본 API의 photos는 배열(여러 장)이지만,
  // 정규화(별도 테이블)하면 개발 시간이 더 들어서(14시간 예산 안에서) 문자열 1개로 평탄화
  // → 대표 이미지 1장만 저장, 나머지는 버림

  @Column(columnDefinition = "TEXT")
  private String info;
  // content-info도 같은 이유로 평탄화. 나중에 정규화하려면 별도 테이블 + 연관관계로 교체 필요

  @Column(nullable = false)
  private LocalDateTime pubDate;
  // 재단 원본의 발행/수정 시점. 배치 동기화 때
  // "이 값이 DB에 저장된 값보다 최신이면 업데이트" 판단 기준으로 사용

  @Column
  private LocalDate startDate; // 행사 시작일

  @Column
  private LocalDate endDate; // 행사 종료일

  @Column(length = 1000)
  private String link; // 재단 원문 상세 페이지 URL

  @Builder
  public Event(Long idx, String title, String category, Double latitude,
      Double longitude, String imageUrl, String info, LocalDateTime pubDate, LocalDate startDate, LocalDate endDate,
      String link) {
    this.idx = idx;
    this.title = title;
    this.category = category;
    this.latitude = latitude;
    this.longitude = longitude;
    this.imageUrl = imageUrl;
    this.info = info;
    this.pubDate = pubDate;
    this.startDate = startDate;
    this.endDate = endDate;
    this.link = link;
  }

  // 재단 원본이 바뀌었거나, 예전에 저장할 때 빠진 값(link 등)이 있으면 최신 값으로 덮어씀
  // (idx·id는 그대로 → 찜 목록 연결이 끊기지 않음)
  public void updateFrom(Event latest) {
    this.title = latest.title;
    this.category = latest.category;
    this.latitude = latest.latitude;
    this.longitude = latest.longitude;
    this.imageUrl = latest.imageUrl;
    this.info = latest.info;
    this.pubDate = latest.pubDate;
    this.startDate = latest.startDate;
    this.endDate = latest.endDate;
    this.link = latest.link;
  }
}
