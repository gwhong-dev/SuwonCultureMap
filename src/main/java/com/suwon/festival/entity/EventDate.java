package com.suwon.festival.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 행사가 실제로 진행되는 날 하나 (V5). event.schedule_status 가 DATES 일 때만 행이 있음
@Entity
@Table(name = "event_date")
@IdClass(EventDate.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventDate {

  @Id
  @Column(name = "event_id")
  private Long eventId;

  @Id
  @Column(name = "run_date")
  private LocalDate runDate;
  // date 는 MySQL · H2 예약어와 겹쳐서 run_date

  public EventDate(Long eventId, LocalDate runDate) {
    this.eventId = eventId;
    this.runDate = runDate;
  }

  @NoArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    private Long eventId;
    private LocalDate runDate;
  }
}
