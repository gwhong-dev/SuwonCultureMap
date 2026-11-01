package com.suwon.festival.service.merge;

import java.time.LocalDate;

import com.suwon.festival.entity.Event;

// 테스트용 가상 행사 (실데이터 아님)
final class MergeFixtures {

  // 가상 공연장 좌표 (수원 근처 임의 값)
  static final double LAT = 37.2800;
  static final double LNG = 127.0150;

  private MergeFixtures() {
  }

  static LocalDate d(String s) {
    return LocalDate.parse(s);
  }

  static MergeCandidate swcf(long id, String title, String start, String end) {
    return new MergeCandidate(id, Event.SOURCE_SWCF, title, "가상 아트홀", LAT, LNG,
        start == null ? null : d(start), end == null ? null : d(end), false);
  }

  static MergeCandidate insta(long id, String title, String start, String end, boolean roundup) {
    return new MergeCandidate(id, Event.SOURCE_INSTAGRAM, title, "가상 아트홀", LAT + 0.0005, LNG,
        start == null ? null : d(start), end == null ? null : d(end), roundup);
  }

  static MergeCandidate at(MergeCandidate c, String place, Double lat, Double lng) {
    return new MergeCandidate(c.id(), c.source(), c.title(), place, lat, lng, c.startDate(), c.endDate(), c.roundup());
  }
}
