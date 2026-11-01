package com.suwon.festival.service.merge;

import java.time.LocalDate;

import com.suwon.festival.entity.Event;

// 합치기 비교에 쓰는 행사 1건 (설계 docs/event-merge.md 3번)
// - DB · 엔티티와 떼어 둔 순수 값 → 규칙을 DB 없이 단위 테스트
// - roundup: 인스타 "모음 글"(행사로 읽힌 블록이 2개 이상인 게시물)에서 나온 행사인지. EventMergeService 가 채움
public record MergeCandidate(
    Long id,
    String source,
    String title,
    String place,
    Double latitude,
    Double longitude,
    LocalDate startDate,
    LocalDate endDate,
    boolean roundup) {

  public boolean isFoundation() {
    return Event.SOURCE_SWCF.equals(source);
  }

  public static MergeCandidate from(Event e, boolean roundup) {
    return new MergeCandidate(e.getId(), e.getSource(), e.getTitle(), e.getPlace(),
        e.getLatitude(), e.getLongitude(), e.getStartDate(), e.getEndDate(), roundup);
  }
}
