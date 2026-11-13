package com.suwon.festival.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 명소 (V10). 행사(Event)와 섞지 않는 별도 테이블 — 결정 docs/decisions/2026-11-13-foundation-api-scope.md
//  - 출처(source) + 출처 번호(source_key) 로 한 곳을 가리킴. 재동기화 때 지우지 않고 갱신 → id 유지 (② 찜이 id 를 씀)
//  - 좌표는 없을 수 있음 (목록에만 보여 줌). coordSource = 좌표를 어디서 얻었는지
@Entity
@Table(name = "landmark", uniqueConstraints = @UniqueConstraint(columnNames = { "source", "source_key" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicUpdate
public class Landmark {

  public static final String SOURCE_FORTRESS = "SWCF_FORTRESS"; // 재단 59_C 수원화성 시설물
  public static final String SOURCE_PALACE = "SWCF_PALACE";     // 재단 63_C 화성행궁 시설물

  public static final String COORD_API = "API";     // 재단 응답 좌표
  public static final String COORD_KAKAO = "KAKAO"; // 카카오 장소 검색으로 보충

  public static final int NAME_MAX = 255;
  public static final int HISTORY_MAX = 500;
  public static final int URL_MAX = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String source;

  @Column(name = "source_key", nullable = false, length = 100)
  private String sourceKey; // 재단 idx

  @Column(nullable = false, length = NAME_MAX)
  private String name; // 제목에서 "- 비공개" 를 뗀 이름 (한자 병기는 그대로)

  @Column(nullable = false, length = 50)
  private String kind; // 수원화성 / 화성행궁

  private Double latitude;  // 위도 37.x (재단 응답은 lat · lng 가 뒤바뀌어 옴 → 바로잡아 저장)

  private Double longitude; // 경도 127.x

  @Column(name = "coord_source", length = 20)
  private String coordSource;

  @Column(length = HISTORY_MAX)
  private String history; // 창건 · 복원 한 줄 ("1794년(정조 18) 창건 · 1975년 문루 복원")

  @Column(name = "non_public", nullable = false)
  private boolean nonPublic; // 비공개(관람 불가) — 빼지 않고 저장, 화면에서 강조

  @Column(name = "image_url", length = URL_MAX)
  private String imageUrl; // 대표 사진 원본 주소 (파일은 받아 두지 않음)

  @Column(length = URL_MAX)
  private String link; // 재단 원문

  @Column(columnDefinition = "TEXT")
  private String description; // 국문 본문 (HTML 제거, 영문 단락 제외)

  @Column(name = "pub_date")
  private LocalDateTime pubDate; // 재단 게시 · 수정 시각 (참고용)

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt; // 내용(좌표 포함)이 바뀐 시각

  @Column(name = "last_seen_at", nullable = false)
  private LocalDateTime lastSeenAt; // 마지막으로 출처 응답에 있던 시각

  // 출처에서 받은 내용 (좌표 제외 — 좌표는 setCoordinates 로 따로)
  public record Content(String name, String kind, String history, boolean nonPublic, String imageUrl, String link,
      String description, LocalDateTime pubDate) {
  }

  public static Landmark create(String source, String sourceKey, Content content, LocalDateTime now) {
    Landmark l = new Landmark();
    l.source = source;
    l.sourceKey = sourceKey;
    l.apply(content);
    l.createdAt = now;
    l.updatedAt = now;
    l.lastSeenAt = now;
    return l;
  }

  // 내용이 달라졌으면 바꾸고 true
  public boolean apply(Content c) {
    boolean changed = !Objects.equals(name, c.name()) || !Objects.equals(kind, c.kind())
        || !Objects.equals(history, c.history()) || nonPublic != c.nonPublic()
        || !Objects.equals(imageUrl, c.imageUrl()) || !Objects.equals(link, c.link())
        || !Objects.equals(description, c.description()) || !Objects.equals(pubDate, c.pubDate());
    if (changed) {
      name = c.name();
      kind = c.kind();
      history = c.history();
      nonPublic = c.nonPublic();
      imageUrl = c.imageUrl();
      link = c.link();
      description = c.description();
      pubDate = c.pubDate();
    }
    return changed;
  }

  // 좌표가 달라졌으면 바꾸고 true. lat · lng 는 바로잡은 값 (위도 · 경도)
  public boolean setCoordinates(Double lat, Double lng, String from) {
    boolean changed = !Objects.equals(latitude, lat) || !Objects.equals(longitude, lng)
        || !Objects.equals(coordSource, from);
    if (changed) {
      latitude = lat;
      longitude = lng;
      coordSource = lat == null ? null : from;
    }
    return changed;
  }

  public boolean hasCoordinates() {
    return latitude != null && longitude != null;
  }

  public void touchUpdated(LocalDateTime now) {
    updatedAt = now;
  }

  public void markSeen(LocalDateTime now) {
    lastSeenAt = now;
  }
}
