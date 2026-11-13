package com.suwon.festival.service.landmark;

import com.suwon.festival.entity.Landmark;

// 재단 명소 API 코드 2개 (인증키는 코드마다 다름 → LandmarkClient)
//  - 75_C 테마체험은 59_C 성신사와 같은 내용이라 받지 않음 (결정 docs/decisions/2026-11-13-foundation-api-scope.md)
//  - area: 카카오 장소 검색 보충 좌표를 인정하는 범위 (같은 이름의 다른 곳을 찍지 않게, 넉넉하게)
public enum LandmarkFeed {

  FORTRESS("59_C", Landmark.SOURCE_FORTRESS, "수원화성", 37.268, 37.296, 126.998, 127.032),
  PALACE("63_C", Landmark.SOURCE_PALACE, "화성행궁", 37.278, 37.287, 127.008, 127.018);

  public final String code;
  public final String source;
  public final String kind;
  final double latMin, latMax, lngMin, lngMax;

  LandmarkFeed(String code, String source, String kind, double latMin, double latMax, double lngMin, double lngMax) {
    this.code = code;
    this.source = source;
    this.kind = kind;
    this.latMin = latMin;
    this.latMax = latMax;
    this.lngMin = lngMin;
    this.lngMax = lngMax;
  }

  boolean inArea(double lat, double lng) {
    return lat >= latMin && lat <= latMax && lng >= lngMin && lng <= lngMax;
  }
}
