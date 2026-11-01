package com.suwon.festival.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// Favorite.java
@Entity
@Table(name = "favorite", uniqueConstraints = @UniqueConstraint(columnNames = { "member_id", "event_id" })
// 같은 회원이 같은 이벤트를 두 번 즐겨찾기하지 못하게 DB 레벨에서 차단
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  // 단순 PK 채택. (memberId, eventId) 복합키도 가능하지만
  // 초보 단계에서는 단순 PK + unique 제약 조합이 다루기 쉬워서 이걸 선택

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;
  // Favorite → Member 단방향.
  // Member 쪽에 List<Favorite>를 걸지 않는 이유:
  // 양방향으로 걸면 Member를 JSON으로 직렬화할 때 Favorite → Member → Favorite …
  // 순환참조가 발생할 수 있음. 지금은 필요 없으니 단방향으로 충분

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;
  // 위와 동일한 이유로 Favorite → Event 단방향만 유지

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
  // 즐겨찾기한 시점. updatable = false로 막아서 한번 기록되면 수정 못하게 함
  // (즐겨찾기 취소는 row 삭제로 처리하고, 이 값 자체를 고치는 로직은 만들지 않음)

  @Builder
  public Favorite(Member member, Event event, LocalDateTime createdAt) {
    this.member = member;
    this.event = event;
    this.createdAt = createdAt;
  }

  // 인스타 옛 블록을 내릴 때 뒤를 잇는 행사로 옮김 (찜한 시점은 그대로)
  public void moveTo(Event event) {
    this.event = event;
  }
}
